package com.csm.server

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * 商品存储层（SQLite）。
 *
 * 为什么用 SQLite 而不是内存 List：课程要求「云存储」，服务端重启后数据不能丢；
 * 而引入 MySQL/Postgres 又要额外装环境，实验课机器上跑不起来。SQLite 单文件、
 * 零配置，随进程起停，正好够用。
 *
 * 所有方法都加 `@Synchronized`：SQLite 单连接不支持并发写，串行化是最省心的正确做法，
 * 这个接口量（几十 QPS）完全够。
 */
class ProductStore private constructor(private val connection: Connection) {

    @Synchronized
    fun list(
        page: Int,
        size: Int,
        category: String?,
        keyword: String?,
        minPrice: Double?,
        maxPrice: Double?,
        condition: String?,
        sort: String?,
        // 只看某个卖家发布的：我的发布页用，null 表示不限制
        sellerId: String? = null
    ): ProductPage {
        val where = StringBuilder("WHERE status = 'ON_SALE'")
        val args = mutableListOf<Any>()

        if (!category.isNullOrBlank()) {
            where.append(" AND category = ?")
            args += category
        }
        if (!keyword.isNullOrBlank()) {
            where.append(" AND (title LIKE ? OR description LIKE ?)")
            args += "%$keyword%"
            args += "%$keyword%"
        }
        if (minPrice != null) {
            where.append(" AND price >= ?")
            args += minPrice
        }
        if (maxPrice != null) {
            where.append(" AND price <= ?")
            args += maxPrice
        }
        if (!condition.isNullOrBlank()) {
            where.append(" AND condition = ?")
            args += condition
        }
        if (!sellerId.isNullOrBlank()) {
            where.append(" AND seller_id = ?")
            args += sellerId
        }

        val total = connection.prepareStatement("SELECT COUNT(*) FROM products $where").use { ps ->
            args.forEachIndexed { index, value -> ps.setObject(index + 1, value) }
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

        // sort 只认白名单：直接拼用户输入会变成 SQL 注入
        val orderBy = when (sort) {
            "priceAsc" -> "price ASC, published_at DESC"
            "priceDesc" -> "price DESC, published_at DESC"
            // nearby 需要经纬度，第 10 次提交再在服务端做 Haversine 排序
            else -> "published_at DESC"
        }

        val safePage = page.coerceAtLeast(1)
        val safeSize = size.coerceIn(1, 50)
        val offset = (safePage - 1) * safeSize

        // 基准价只需算一次，避免每行商品都扫一遍全表
        val stats = categoryStats()
        val items = connection.prepareStatement(
            "SELECT * FROM products $where ORDER BY $orderBy LIMIT ? OFFSET ?"
        ).use { ps ->
            args.forEachIndexed { index, value -> ps.setObject(index + 1, value) }
            ps.setInt(args.size + 1, safeSize)
            ps.setInt(args.size + 2, offset)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(rs.toProduct())
                }
            }
        }.map { it.withSuggestion(stats) }

        return ProductPage(
            page = safePage,
            size = safeSize,
            total = total,
            hasMore = offset + items.size < total,
            items = items
        )
    }

    @Synchronized
    fun find(id: String): ProductDto? = connection.prepareStatement(
        "SELECT * FROM products WHERE id = ? AND status = 'ON_SALE'"
    ).use { ps ->
        ps.setString(1, id)
        ps.executeQuery().use { rs ->
            if (rs.next()) rs.toProduct().withSuggestion(categoryStats()) else null
        }
    }

    @Synchronized
    fun create(
        request: CreateProductRequest,
        now: OffsetDateTime = OffsetDateTime.now(),
        // 样例数据用它来区分卖家；真实发布走默认值 u-me
        sellerId: String = ME
    ): ProductDto {
        val id = "p" + System.currentTimeMillis()
        connection.prepareStatement(
            """
            INSERT INTO products(id, title, price, original_price, category, condition,
                                 description, location, published_at, seller_id, seller_name,
                                 cover, images, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ON_SALE')
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, id)
            ps.setString(2, request.title.trim())
            ps.setDouble(3, request.price)
            if (request.originalPrice == null) ps.setNull(4, java.sql.Types.REAL)
            else ps.setDouble(4, request.originalPrice)
            ps.setString(5, request.category)
            ps.setString(6, request.condition)
            ps.setString(7, request.description)
            ps.setString(8, request.location)
            ps.setString(9, now.toString())
            ps.setString(10, sellerId)
            ps.setString(11, request.sellerName)
            // cover 与 images 同源：images 存换行分隔的完整列表，cover 只是第一张的冗余，
            // 列表页少解析一次就能拿到封面
            ps.setString(12, request.images.firstOrNull())
            ps.setString(13, request.images.joinToString("\n"))
            ps.executeUpdate()
        }
        return find(id) ?: error("刚插入的商品读不回来：$id")
    }

    /** 下架用软删除：留言、收藏还引用着这条记录，物理删除会留下悬空引用。 */
    @Synchronized
    fun takeDown(id: String): Boolean = connection.prepareStatement(
        "UPDATE products SET status = 'TAKEN_DOWN' WHERE id = ? AND status = 'ON_SALE'"
    ).use { ps ->
        ps.setString(1, id)
        ps.executeUpdate() > 0
    }

    // ---- 留言 ----

    @Synchronized
    fun listMessages(productId: String): List<MessageDto> = connection.prepareStatement(
        "SELECT * FROM messages WHERE product_id = ? ORDER BY created_at ASC"
    ).use { ps ->
        ps.setString(1, productId)
        ps.executeQuery().use { rs ->
            buildList { while (rs.next()) add(rs.toMessage()) }
        }
    }

    /**
     * 发一条留言。
     *
     * 返回 null 表示商品不存在或已下架：留言是挂在商品上的，商品没了两边都对不上。
     * 当前没有账号体系，`sender_id` 固定写 `u-me`，靠请求里的 `senderName` 区分昵称。
     */
    @Synchronized
    fun addMessage(
        productId: String,
        request: CreateMessageRequest,
        now: OffsetDateTime = OffsetDateTime.now()
    ): MessageDto? {
        if (find(productId) == null) return null
        return insertMessage(
            productId = productId,
            senderId = ME,
            senderName = request.senderName.trim().ifEmpty { "我" },
            content = request.content.trim(),
            now = now
        )
    }

    /**
     * 未读数：桌面小组件与「我的」页角标都读这个值。
     *
     * 只算别人发来的：自己发出去的留言永远不算「未读」，否则角标会一直骗人。
     */
    @Synchronized
    fun unreadCount(): Int = connection.prepareStatement(
        "SELECT COUNT(*) FROM messages WHERE read = 0 AND sender_id <> ?"
    ).use { ps ->
        ps.setString(1, ME)
        ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    @Synchronized
    fun markRead(id: String): Boolean = connection.prepareStatement(
        "UPDATE messages SET read = 1 WHERE id = ?"
    ).use { ps ->
        ps.setString(1, id)
        ps.executeUpdate() > 0
    }

    /**
     * 「我的留言」会话列表：每个商品一条，带最后一条留言与未读数。
     *
     * 用一条聚合 SQL 取，而不是「先查商品再逐个查留言」：
     * 后者在有 N 个会话时要打 N+1 次库，列表一滚就卡。
     * SQLite 允许在 GROUP BY 里直接取非聚合列（`p.title`），因为它按分组取值。
     */
    @Synchronized
    fun conversations(): List<ConversationDto> = connection.prepareStatement(
        """
        SELECT m.product_id AS product_id,
               p.title AS title,
               p.cover AS cover,
               MAX(m.created_at) AS last_at,
               (SELECT content FROM messages
                 WHERE product_id = m.product_id
                 ORDER BY created_at DESC LIMIT 1) AS last_content,
               SUM(CASE WHEN m.read = 0 AND m.sender_id <> ? THEN 1 ELSE 0 END) AS unread
          FROM messages m
          JOIN products p ON p.id = m.product_id
         GROUP BY m.product_id
         ORDER BY last_at DESC
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, ME)
        ps.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        ConversationDto(
                            productId = rs.getString("product_id"),
                            productTitle = rs.getString("title"),
                            cover = rs.getString("cover"),
                            lastContent = rs.getString("last_content") ?: "",
                            lastAt = OffsetDateTime.parse(rs.getString("last_at"))
                                .toString(),
                            unreadCount = rs.getInt("unread")
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun priceStats(category: String?): PriceStatsDto {
        val sql = if (category.isNullOrBlank()) {
            "SELECT price FROM products WHERE status = 'ON_SALE'"
        } else {
            "SELECT price FROM products WHERE status = 'ON_SALE' AND category = ?"
        }
        val prices = connection.prepareStatement(sql).use { ps ->
            if (!category.isNullOrBlank()) ps.setString(1, category)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getDouble(1)) }
            }
        }
        return PriceStatsDto(
            category = category.orEmpty(),
            median = round2(median(prices)),
            p25 = percentile(prices, 0.25),
            p75 = percentile(prices, 0.75),
            sampleCount = prices.size
        )
    }

    /**
     * 每个分类的价格基准，列表接口一次算完再复用，避免每行都查一次库。
     *
     * 迭代 2 修订：单看「同类目价格中位数」会被极端样本带偏（1280 的 iPad 与 45 的充电宝同一分类，
     * 中位数 210 会把充电宝判成捡漏）。改成「同类目折扣率中位数 × 本商品原价」更贴近真实心智：
     * 同样原价 129 的充电宝，按同类的「二手/原价」比例折价才合理。
     * 老算法保留为兜底：没有原价、或分类里没人填原价时，仍用类目价格中位数。
     */
    @Synchronized
    fun categoryStats(): Map<String, CategoryStat> = connection.prepareStatement(
        "SELECT category, price, original_price FROM products WHERE status = 'ON_SALE'"
    ).use { ps ->
        ps.executeQuery().use { rs ->
            val prices = mutableMapOf<String, MutableList<Double>>()
            val ratios = mutableMapOf<String, MutableList<Double>>()
            while (rs.next()) {
                val category = rs.getString("category")
                val price = rs.getDouble("price")
                prices.getOrPut(category) { mutableListOf() } += price
                val original = rs.getDouble("original_price")
                if (!rs.wasNull() && original > 0 && price > 0 && price <= original) {
                    ratios.getOrPut(category) { mutableListOf() } += price / original
                }
            }
            (prices.keys + ratios.keys).associateWith { category ->
                CategoryStat(
                    medianPrice = round2(median(prices[category].orEmpty())),
                    medianRatio = round2(median(ratios[category].orEmpty())),
                    ratioSample = ratios[category].orEmpty().size
                )
            }
        }
    }

    private fun ProductDto.withSuggestion(stats: Map<String, CategoryStat>): ProductDto {
        val stat = stats[category]
        val base = when {
            originalPrice != null && originalPrice > 0 && (stat?.ratioSample ?: 0) > 0 ->
                round2(originalPrice * (stat!!.medianRatio))
            else -> stat?.medianPrice?.takeIf { it > 0 } ?: price
        }
        val days = daysSince(publishedAt)
        val suggest = suggestPrice(base, condition, days)
        return copy(
            suggestPrice = suggest,
            isBargain = price <= suggest * 0.70
        )
    }

    private fun daysSince(isoTime: String): Long = try {
        val published = OffsetDateTime.parse(isoTime)
        java.time.Duration.between(published, OffsetDateTime.now()).toDays().coerceAtLeast(0)
    } catch (e: DateTimeParseException) {
        0
    }

    private fun insertMessage(
        productId: String,
        senderId: String,
        senderName: String,
        content: String,
        now: OffsetDateTime
    ): MessageDto {
        val id = "m" + System.nanoTime()
        connection.prepareStatement(
            """
            INSERT INTO messages(id, product_id, sender_id, sender_name, content, created_at, read)
            VALUES (?, ?, ?, ?, ?, ?, 0)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, id)
            ps.setString(2, productId)
            ps.setString(3, senderId)
            ps.setString(4, senderName)
            ps.setString(5, content)
            ps.setString(6, now.toString())
            ps.executeUpdate()
        }
        return MessageDto(
            id = id,
            productId = productId,
            senderName = senderName,
            content = content,
            createdAt = now.toString(),
            isMine = senderId == ME,
            read = false
        )
    }

    private fun java.sql.ResultSet.toMessage(): MessageDto = MessageDto(
        id = getString("id"),
        productId = getString("product_id"),
        senderName = getString("sender_name"),
        content = getString("content"),
        createdAt = getString("created_at"),
        isMine = getString("sender_id") == ME,
        read = getInt("read") == 1
    )

    private fun java.sql.ResultSet.toProduct(): ProductDto {
        val imagesRaw = getString("images").orEmpty()
        return ProductDto(
            id = getString("id"),
            title = getString("title"),
            price = getDouble("price"),
            originalPrice = getDouble("original_price").takeUnless { wasNull() },
            category = getString("category"),
            condition = getString("condition"),
            description = getString("description").orEmpty(),
            location = getString("location").orEmpty(),
            publishedAt = getString("published_at"),
            seller = SellerDto(getString("seller_id"), getString("seller_name")),
            cover = getString("cover"),
            images = if (imagesRaw.isBlank()) emptyList() else imagesRaw.split("\n")
        )
    }

    companion object {
        /** 当前演示用的「我」；接入登录后换成真实用户 id。 */
        private const val ME = "u-me"

        /**
         * 打开数据库（不存在则建表 + 灌样例数据）。
         * 默认落在 `server/data/csm.db`，可用 `-Dcsm.db=...` 覆盖，测试时可指向临时文件。
         */
        fun open(dbPath: String = System.getProperty("csm.db", "data/csm.db")): ProductStore {
            Class.forName("org.sqlite.JDBC")
            val file = File(dbPath)
            file.parentFile?.mkdirs()
            val connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS products(
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        price REAL NOT NULL,
                        original_price REAL,
                        category TEXT NOT NULL,
                        condition TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        location TEXT NOT NULL DEFAULT '',
                        published_at TEXT NOT NULL,
                        seller_id TEXT NOT NULL,
                        seller_name TEXT NOT NULL,
                        cover TEXT,
                        images TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'ON_SALE'
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS messages(
                        id TEXT PRIMARY KEY,
                        product_id TEXT NOT NULL,
                        sender_id TEXT NOT NULL,
                        sender_name TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        read INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
            val store = ProductStore(connection)
            store.seedIfEmpty()
            store.seedMessagesIfEmpty()
            return store
        }

        /** 样例数据：和 App 端 `SampleData` 保持同样的商品气质，联调时一眼能对上。 */
        private val seeds: List<CreateProductRequest> = listOf(
            CreateProductRequest("考研数学复习全书（九成新）", 32.0, 68.0, "BOOK", "GOOD", "只做过前两章，无笔记，书角轻微磨损。", "南苑 3 号楼", "李同学"),
            CreateProductRequest("二手 iPad 2021 64G", 1280.0, 2499.0, "DIGITAL", "LIKE_NEW", "电池健康 92%，屏幕无划痕，送保护壳。", "北苑宿舍楼下", "王同学"),
            CreateProductRequest("小米充电宝 20000mAh", 45.0, 129.0, "DIGITAL", "GOOD", "容量足，接口正常，轻微使用痕迹。", "一食堂门口", "陈同学"),
            CreateProductRequest("大学英语四级真题集", 12.0, 39.0, "BOOK", "FAIR", "写过部分答案，可撕掉重做。", "图书馆二楼", "赵同学"),
            CreateProductRequest("自行车（毕业出）", 260.0, 899.0, "SPORTS", "GOOD", "骑了两年，刹车刚换过，车锁一起送。", "东区车库", "周同学"),
            CreateProductRequest("宜家落地灯", 55.0, 199.0, "DAILY", "LIKE_NEW", "搬宿舍带不走了，灯罩完好。", "西苑 6 号楼", "孙同学"),
            CreateProductRequest("优衣库羽绒服 L 码", 180.0, 599.0, "CLOTHING", "GOOD", "穿过一个冬天，无破损。", "南门快递点", "钱同学"),
            CreateProductRequest("机械键盘 87 键", 210.0, 499.0, "DIGITAL", "LIKE_NEW", "青轴，手感很好，宿舍用有点吵所以出。", "北苑 2 号楼", "吴同学"),
            CreateProductRequest("考研英语词汇书", 9.9, 45.0, "BOOK", "POOR", "封面有折痕，内页完整。", "图书馆一楼", "郑同学"),
            CreateProductRequest("瑜伽垫 加厚", 30.0, 89.0, "SPORTS", "LIKE_NEW", "只用过几次，已清洁。", "体育馆门口", "冯同学"),
            CreateProductRequest("床上书桌", 25.0, 79.0, "DAILY", "FAIR", "桌面有划痕，承重没问题。", "西苑 4 号楼", "褚同学"),
            CreateProductRequest("卫衣 男女同款 M", 40.0, 159.0, "CLOTHING", "GOOD", "洗过两次，不起球。", "南苑 1 号楼", "卫同学"),
            // 下面两条故意挂得比同品类折扣率低，用来演示「捡漏」标记（price ≤ 建议价 × 70%）
            CreateProductRequest("Kindle Paperwhite 4", 150.0, 998.0, "DIGITAL", "GOOD", "屏幕无亮点，送原装保护套，急着搬宿舍所以低价出。", "北苑 5 号楼", "蒋同学"),
            CreateProductRequest("尤克里里 23 寸", 60.0, 399.0, "OTHER", "LIKE_NEW", "买来学过一阵，音准正常，送琴包和拨片。", "大学生活动中心", "沈同学")
        )
    }

    /**
     * 样例留言：让「留言」入口一打开就有内容，录屏演示不必先自己发几条。
     * 只在 messages 表为空时灌一次，真实使用中删掉也不会再回来。
     */
    private fun seedMessagesIfEmpty() {
        val count = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM messages").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
        if (count > 0) return
        val products = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id FROM products ORDER BY published_at DESC LIMIT 3").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
        val now = OffsetDateTime.now()
        products.forEachIndexed { index, productId ->
            insertMessage(
                productId = productId,
                senderId = "u-lin",
                senderName = "林同学",
                content = "同学你好，这个还在吗？",
                now = now.minusMinutes(30L + index)
            )
            insertMessage(
                productId = productId,
                senderId = ME,
                senderName = "我",
                content = "在的，可以约在图书馆门口看货。",
                now = now.minusMinutes(25L + index)
            )
        }
    }

    private fun seedIfEmpty() {
        val count = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM products").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
        if (count > 0) return

        val base = OffsetDateTime.now().minusHours(3)
        seeds.forEachIndexed { index, seed ->
            // 样例的发布时间依次往前推，列表看起来才像真的有人在陆续发布。
            // 每 3 条留 1 条给「我」，这样「我的发布」页有内容可看，其余挂在别的卖家名下。
            val sellerId = if (index % 3 == 0) ME else "u-seed-$index"
            create(seed, base.minusHours(index * 5L), sellerId)
        }
    }
}

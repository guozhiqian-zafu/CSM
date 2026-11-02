package com.csm.market.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * 图片压缩流水线（B1 的强制技术点之一「图片处理」）。
 *
 * 三步，每一步都有明确的目的：
 * 1. **采样解码**：先用 inJustDecodeBounds 读尺寸，按 2 的幂做 inSampleSize，
 *    保证解码阶段就不把 4000×3000 的原图整张读进内存（否则低端机会 OOM）；
 * 2. **旋正**：手机竖拍的照片像素其实是横的，方向记录在 EXIF 的 Orientation 里。
 *    这里用 Matrix 把像素转正，之后就不需要再传 EXIF 方向了；
 * 3. **重编码**：Bitmap.compress 生成的 JPEG 不带任何原始 EXIF，
 *    拍摄地点（GPS）、设备型号等隐私信息自然被丢掉——这是「去 GPS」最省事的做法。
 *
 * 输出统一长边 1280px、质量 82%：校园二手商品的实拍图够看清瑕疵，
 * 一张约 150–300KB，宿舍弱网也能传得动。
 */
object ImageCompressor {

    const val MAX_DIMENSION = 1280
    const val JPEG_QUALITY = 82

    /** 压缩结果：文件 + 原始尺寸，用于在 UI 上告诉用户「已压缩」。 */
    data class Result(val file: File, val originalWidth: Int, val originalHeight: Int, val bytes: Int)

    suspend fun compress(context: Context, uri: Uri, index: Int = 0): Result =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val originalWidth = bounds.outWidth
            val originalHeight = bounds.outHeight
            require(originalWidth > 0 && originalHeight > 0) { "这张图片读不出来，换一张试试" }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(originalWidth, originalHeight, MAX_DIMENSION)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: throw IllegalStateException("图片解码失败")

            val rotated = applyOrientation(context, uri, decoded)

            val scaled = scaleDown(rotated, MAX_DIMENSION)
            if (scaled !== rotated) rotated.recycle()

            val outFile = File(
                File(context.cacheDir, "upload").apply { mkdirs() },
                "csm_${System.currentTimeMillis()}_$index.jpg"
            )
            outFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            scaled.recycle()

            Result(
                file = outFile,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                bytes = outFile.length().toInt()
            )
        }

    /**
     * 采样率必须是 2 的幂，BitmapFactory 只认这种值。
     * 循环条件用「采样后仍然大于目标尺寸」，避免一次采到太小导致糊。
     */
    internal fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) / 2 >= target) {
            sample *= 2
        }
        return sample
    }

    private fun applyOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            // 读不到 EXIF（比如 PNG、或者某些云盘返回的 Uri）就当没有方向信息
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap, target: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= target) return bitmap
        val ratio = target.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}

package com.csm.market

import android.app.Application

/**
 * 应用入口。
 *
 * 选择在这里做全局初始化（后续会挂载 Room、Retrofit、图片加载器等），
 * 避免把初始化逻辑散落在 Activity 中，也方便单元测试替换依赖。
 */
class CsmApp : Application()

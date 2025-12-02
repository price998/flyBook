package com.example.myapplication.data

import com.example.myapplication.data.db.RecommendedTopicEntity

/**
 * 默认推荐话题数据
 * 将硬编码数据从 ViewModel 中提取出来，便于维护和扩展
 */
object DefaultTopics {

    /**
     * 获取默认话题列表
     */
    fun getDefaultTopics(): List<RecommendedTopicEntity> {
        
        return listOf(
            RecommendedTopicEntity(
                title = "如何制定学习计划？",

                prompt = "请帮我制定一个高效的学习计划，目标是...",

            ),
            RecommendedTopicEntity(
                title = "写一首关于春天的诗",

                prompt = "请写一首关于春天的现代诗，风格要...",

            ),
            RecommendedTopicEntity(
                title = "解释量子力学",
                prompt = "请用通俗易懂的语言解释一下量子力学。",

            )
        )
    }
}

package com.example.myapplication.data

/**
 * 推荐话题数据类
 */
data class RecommendedTopic(
    val title: String,
    val prompt: String
)

/**
 * 默认推荐话题数据
 * 静态展示数据，无需存储到数据库
 */
object DefaultTopics {

    /**
     * 获取默认话题列表
     */
    fun getDefaultTopics(): List<RecommendedTopic> {
        return listOf(
            RecommendedTopic(
                title = "🤝 怎样提升团队协作效率？",
                prompt = "怎样提升团队协作效率？"
            ),
            RecommendedTopic(
                title = "📚 飞书知识问答是什么？",
                prompt = "飞书知识问答是什么？"
            ),
            RecommendedTopic(
                title = "⚛️ 解释量子力学",
                prompt = "请用通俗的语言给我解释量子力学的核心概念。"
            ),
            RecommendedTopic(
                title = "📊 OKR和KPI有什么区别？",
                prompt = "请用通俗的语言给我解释OKR和KPI有什么区别。"
            ),
            RecommendedTopic(
                title = "📝 如何制定学习计划？",
                prompt = "请帮我制定一个高效的学习计划。"
            ),
            RecommendedTopic(
                title = "🌸 写一首关于春天的诗",
                prompt = "请写一首关于春天的现代诗。"
            )
        )
    }
}

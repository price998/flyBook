package com.example.myapplication.utils

import android.content.Context
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle

/**
 * Markwon 工厂类
 * 
 * 职责：
 * - 创建优化配置的 Markwon 实例
 * - 限制代码高亮支持的语言，提升性能
 * - 提供统一的 Markdown 渲染配置
 * 
 * 性能优化：
 * - 只支持常用的 7 种编程语言
 * - 使用预编译的语法定义
 * - 使用轻量级的 Darkula 主题
 */
@PrismBundle(
    includeAll = true,
    grammarLocatorClassName = ".Prism4jGrammarLocator"
)
object MarkwonFactory {
    
    /**
     * 支持的编程语言列表（限制为常用语言以提升性能）
     */
    private val SUPPORTED_LANGUAGES = setOf(
        "kotlin",
        "java",
        "python",
        "javascript",
        "json",
        "xml",
        "sql"
    )
    
    /**
     * 创建优化配置的 Markwon 实例
     * 
     * @param context Android Context
     * @param enableCodeHighlight 是否启用代码高亮（默认启用）
     * @return 配置好的 Markwon 实例
     */
    fun create(context: Context, enableCodeHighlight: Boolean = true): Markwon {
        val builder = Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
        
        // 如果启用代码高亮，添加语法高亮插件
        if (enableCodeHighlight) {
            try {
                // 创建限制语言的 GrammarLocator
                val prism4j = Prism4j(LimitedGrammarLocator())
                
                // 使用轻量级主题
                val syntaxHighlight = SyntaxHighlightPlugin.create(
                    prism4j,
                    Prism4jThemeDarkula.create()
                )
                
                builder.usePlugin(syntaxHighlight)
            } catch (e: Exception) {
                // 如果代码高亮初始化失败，记录日志但不影响其他功能
                android.util.Log.w("MarkwonFactory", "Failed to initialize syntax highlighting", e)
            }
        }
        
        return builder.build()
    }
    
    /**
     * 限制语言的 GrammarLocator
     * 只加载支持的语言，其他语言返回 null（不高亮）
     */
    private class LimitedGrammarLocator : GrammarLocator {
        // 使用默认的 GrammarLocator 作为后备
        private val defaultLocator: GrammarLocator? by lazy {
            try {
                // 尝试使用生成的 GrammarLocator
                val clazz = Class.forName("io.noties.prism4j.Prism4jGrammarLocator")
                clazz.getDeclaredConstructor().newInstance() as GrammarLocator
            } catch (e: Exception) {
                android.util.Log.w("MarkwonFactory", "Failed to load Prism4jGrammarLocator", e)
                null
            }
        }
        
        override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? {
            // 只返回支持的语言的语法定义
            val normalizedLang = language.lowercase()
            return if (SUPPORTED_LANGUAGES.contains(normalizedLang)) {
                defaultLocator?.grammar(prism4j, normalizedLang)
            } else {
                null
            }
        }
        
        override fun languages(): Set<String> {
            return SUPPORTED_LANGUAGES
        }
    }
}

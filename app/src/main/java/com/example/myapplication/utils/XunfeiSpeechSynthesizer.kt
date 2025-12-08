package com.example.myapplication.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.myapplication.BuildConfig
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechSynthesizer
import com.iflytek.cloud.SynthesizerListener

/**
 * 科大讯飞语音合成（TTS）工具类
 * 
 * 用于将文本转换为语音播放
 */
class XunfeiSpeechSynthesizer(private val context: Context) {
    
    companion object {
        private const val TAG = "XunfeiTTS"
        
        // 从 BuildConfig 读取科大讯飞 APPID
        private val APPID: String by lazy {
            BuildConfig.XUNFEI_APPID.also {
                if (it.isEmpty()) {
                    Log.w(TAG, "XUNFEI_APPID is empty! Please set it in local.properties")
                }
            }
        }
        
        // 发音人选项
        const val VOICE_XIAOYAN = "xiaoyan"      // 小燕 - 普通话女声
        const val VOICE_XIAOYU = "xiaoyu"        // 小宇 - 普通话男声
        const val VOICE_XIAOMEI = "xiaomei"      // 小美 - 粤语女声
        const val VOICE_XIAOLIN = "xiaolin"      // 小琳 - 台湾普通话女声
        const val VOICE_XIAORONG = "xiaorong"    // 小蓉 - 四川话女声
    }
    
    private var speechSynthesizer: SpeechSynthesizer? = null
    private var isSpeaking = false
    
    // 回调监听器
    private var onStartListener: (() -> Unit)? = null
    private var onCompleteListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onProgressListener: ((Int) -> Unit)? = null
    
    // 初始化监听器
    private val initListener = InitListener { code ->
        Log.d(TAG, "SpeechSynthesizer init() code = $code")
        if (code != ErrorCode.SUCCESS) {
            Log.e(TAG, "初始化失败，错误码：$code")
        }
    }
    
    // 合成监听器
    private val synthesizerListener = object : SynthesizerListener {
        override fun onSpeakBegin() {
            Log.d(TAG, "开始播放")
            isSpeaking = true
            onStartListener?.invoke()
        }
        
        override fun onSpeakPaused() {
            Log.d(TAG, "暂停播放")
        }
        
        override fun onSpeakResumed() {
            Log.d(TAG, "继续播放")
        }
        
        override fun onBufferProgress(percent: Int, beginPos: Int, endPos: Int, info: String?) {
            // 合成进度
        }
        
        override fun onSpeakProgress(percent: Int, beginPos: Int, endPos: Int) {
            // 播放进度
            onProgressListener?.invoke(percent)
        }
        
        override fun onCompleted(error: SpeechError?) {
            isSpeaking = false
            if (error != null) {
                Log.e(TAG, "播放错误：${error.errorDescription}")
                onErrorListener?.invoke("语音播放错误：${error.errorDescription}")
            } else {
                Log.d(TAG, "播放完成")
                onCompleteListener?.invoke()
            }
        }
        
        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {
            // 扩展用接口
        }
    }
    
    /**
     * 初始化语音合成
     */
    fun init() {
        // 创建语音合成对象
        speechSynthesizer = SpeechSynthesizer.createSynthesizer(context, initListener)
        
        // 设置参数
        setDefaultParams()
    }
    
    /**
     * 设置默认参数
     */
    private fun setDefaultParams() {
        speechSynthesizer?.apply {
            // 设置 APPID
            setParameter(SpeechConstant.APPID, APPID)
            // 设置合成引擎（云端）
            setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
            // 设置发音人
            setParameter(SpeechConstant.VOICE_NAME, VOICE_XIAOYAN)
            // 设置语速（0-100，默认50）
            setParameter(SpeechConstant.SPEED, "50")
            // 设置音调（0-100，默认50）
            setParameter(SpeechConstant.PITCH, "50")
            // 设置音量（0-100，默认50）
            setParameter(SpeechConstant.VOLUME, "80")
            // 设置播放器音频流类型（3 = STREAM_MUSIC）
            setParameter(SpeechConstant.STREAM_TYPE, "3")
        }
    }
    
    /**
     * 开始语音合成并播放
     * 
     * @param text 要播放的文本
     */
    fun speak(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "文本为空，跳过播放")
            return
        }
        
        // 如果正在播放，先停止
        if (isSpeaking) {
            stop()
        }
        
        speechSynthesizer?.let { synthesizer ->
            val ret = synthesizer.startSpeaking(text, synthesizerListener)
            if (ret != ErrorCode.SUCCESS) {
                Log.e(TAG, "合成失败，错误码：$ret")
                onErrorListener?.invoke("启动语音合成失败，错误码：$ret")
            } else {
                Log.d(TAG, "开始合成，文本长度：${text.length}")
            }
        } ?: run {
            onErrorListener?.invoke("语音合成器未初始化")
        }
    }
    
    /**
     * 暂停播放
     */
    fun pause() {
        speechSynthesizer?.pauseSpeaking()
        Log.d(TAG, "暂停播放")
    }
    
    /**
     * 继续播放
     */
    fun resume() {
        speechSynthesizer?.resumeSpeaking()
        Log.d(TAG, "继续播放")
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        speechSynthesizer?.stopSpeaking()
        isSpeaking = false
        Log.d(TAG, "停止播放")
    }
    
    /**
     * 是否正在播放
     */
    fun isSpeaking(): Boolean = isSpeaking
    
    /**
     * 设置发音人
     */
    fun setVoice(voiceName: String) {
        speechSynthesizer?.setParameter(SpeechConstant.VOICE_NAME, voiceName)
    }
    
    /**
     * 设置语速（0-100）
     */
    fun setSpeed(speed: Int) {
        val validSpeed = speed.coerceIn(0, 100)
        speechSynthesizer?.setParameter(SpeechConstant.SPEED, validSpeed.toString())
    }
    
    /**
     * 设置音量（0-100）
     */
    fun setVolume(volume: Int) {
        val validVolume = volume.coerceIn(0, 100)
        speechSynthesizer?.setParameter(SpeechConstant.VOLUME, validVolume.toString())
    }
    
    // ========== 监听器设置 ==========
    
    fun setOnStartListener(listener: () -> Unit) {
        onStartListener = listener
    }
    
    fun setOnCompleteListener(listener: () -> Unit) {
        onCompleteListener = listener
    }
    
    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }
    
    fun setOnProgressListener(listener: (Int) -> Unit) {
        onProgressListener = listener
    }
    
    /**
     * 销毁资源
     */
    fun destroy() {
        speechSynthesizer?.stopSpeaking()
        speechSynthesizer?.destroy()
        speechSynthesizer = null
        isSpeaking = false
    }
}

package com.example.myapplication.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.cloud.SpeechUtility
import org.json.JSONException
import org.json.JSONObject

/**
 * 科大讯飞语音识别工具类
 */
class XunfeiSpeechRecognizer(private val context: Context) {
    
    companion object {
        private const val TAG = "XunfeiSpeechRecognizer"
        // 科大讯飞语音识别配置
        private const val APPID = "b4d78b8e"
        private const val API_SECRET = "YzIwZDIxMTg4YTdjYWJiZjBmZGY2NzBi"
        private const val API_KEY = "a1f629b6be89cf8557864817f23b939b"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var onResultListener: ((String) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onVolumeChangedListener: ((Int) -> Unit)? = null
    
    // 初始化监听器
    private val initListener = InitListener { code ->
        Log.d(TAG, "SpeechRecognizer init() code = $code")
        if (code != ErrorCode.SUCCESS) {
            Log.e(TAG, "初始化失败，错误码：$code")
        }
    }
    
    // 识别监听器
    private val recognizerListener = object : RecognizerListener {
        override fun onBeginOfSpeech() {
            Log.d(TAG, "开始说话")
        }
        
        override fun onError(error: SpeechError) {
            Log.e(TAG, "识别错误：${error.errorDescription}")
            isListening = false
            onErrorListener?.invoke("语音识别错误：${error.errorDescription}")
        }
        
        override fun onEndOfSpeech() {
            Log.d(TAG, "结束说话")
            isListening = false
        }
        
        override fun onResult(results: RecognizerResult?, isLast: Boolean) {
            Log.d(TAG, "识别结果：${results?.resultString}")
            results?.let {
                val text = parseResult(it.resultString)
                if (text.isNotEmpty()) {
                    onResultListener?.invoke(text)
                }
            }
        }
        
        override fun onVolumeChanged(volume: Int, data: ByteArray?) {
            onVolumeChangedListener?.invoke(volume)
        }
        
        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {
            // 扩展用接口
        }
    }
    
    /**
     * 初始化语音识别
     */
    fun init() {
        // 初始化科大讯飞语音引擎
        SpeechUtility.createUtility(context, SpeechConstant.APPID + "=" + APPID)
        
        // 创建语音识别对象
        speechRecognizer = SpeechRecognizer.createRecognizer(context, initListener)
        
        // 设置参数
        setParams()
    }
    
    /**
     * 设置识别参数
     */
    private fun setParams() {
        speechRecognizer?.apply {
            // 设置语法ID和 APPID（必须）
            setParameter(SpeechConstant.APPID, APPID)
            // 设置返回结果格式
            setParameter(SpeechConstant.RESULT_TYPE, "json")
            // 设置语言
            setParameter(SpeechConstant.LANGUAGE, "zh_cn")
            // 设置语言区域
            setParameter(SpeechConstant.ACCENT, "mandarin")
            // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
            setParameter(SpeechConstant.VAD_BOS, "4000")
            // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入
            setParameter(SpeechConstant.VAD_EOS, "1000")
            // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
            setParameter(SpeechConstant.ASR_PTT, "1")
            // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
            // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
            setParameter(SpeechConstant.AUDIO_FORMAT, "wav")
            setParameter(SpeechConstant.ASR_AUDIO_PATH, context.externalCacheDir?.absolutePath + "/msc/asr.wav")
        }
    }
    
    /**
     * 开始语音识别
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "正在识别中，请稍后再试")
            return
        }
        
        speechRecognizer?.let { recognizer ->
            val ret = recognizer.startListening(recognizerListener)
            if (ret != ErrorCode.SUCCESS) {
                Log.e(TAG, "识别失败，错误码：$ret")
                onErrorListener?.invoke("启动识别失败，错误码：$ret")
            } else {
                isListening = true
                Log.d(TAG, "开始识别")
            }
        } ?: run {
            onErrorListener?.invoke("语音识别器未初始化")
        }
    }
    
    /**
     * 停止语音识别
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        Log.d(TAG, "停止识别")
    }
    
    /**
     * 取消语音识别
     */
    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
        Log.d(TAG, "取消识别")
    }
    
    /**
     * 是否正在识别
     */
    fun isListening(): Boolean = isListening
    
    /**
     * 解析识别结果
     */
    private fun parseResult(resultString: String): String {
        val result = StringBuilder()
        try {
            val resultJson = JSONObject(resultString)
            val ws = resultJson.getJSONArray("ws")
            for (i in 0 until ws.length()) {
                val items = ws.getJSONObject(i).getJSONArray("cw")
                for (j in 0 until items.length()) {
                    val word = items.getJSONObject(j).getString("w")
                    result.append(word)
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "解析结果失败", e)
        }
        return result.toString()
    }
    
    /**
     * 设置识别结果监听器
     */
    fun setOnResultListener(listener: (String) -> Unit) {
        onResultListener = listener
    }
    
    /**
     * 设置错误监听器
     */
    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }
    
    /**
     * 设置音量变化监听器
     */
    fun setOnVolumeChangedListener(listener: (Int) -> Unit) {
        onVolumeChangedListener = listener
    }
    
    /**
     * 销毁资源
     */
    fun destroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}

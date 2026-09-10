package com.moaradc.mono.web

import android.webkit.JavascriptInterface
import com.moaradc.mono.MainActivity
import com.moaradc.mono.data.Db
import com.moaradc.mono.util.Prefs
import com.moaradc.mono.util.U
import org.json.JSONObject

/**
 * 注入网页的脚本 + 原生桥。
 *
 * 核心思路：钩住页面里所有 <video>：
 *  - 播放时在页面右上角浮出「小窗播放」胶囊按钮；
 *  - 点击后调用 video.requestFullscreen()，由 WebChromeClient.onShowCustomView
 *    接管视频画面 —— 任何站点自研播放器 UI 即被 Mono 播控层替换；
 *  - 播放进度上报给「播放记录」，实现跨会话续播。
 */
object MonoJs {

    /** 页面加载完成后注入 */
    const val HOOK = """(function(){
  if (window.__monoInstalled) return; window.__monoInstalled = true;
  var M = { video:null, pill:null, ph:null, phPlace:null, resumed:false };
  function qsa(s){ try{ return Array.prototype.slice.call(document.querySelectorAll(s)); }catch(e){ return []; } }
  function activeVideo(){
    var vs = qsa('video');
    for (var i=0;i<vs.length;i++){ if (!vs[i].paused && !vs[i].ended) return vs[i]; }
    for (var j=0;j<vs.length;j++){ if (vs[j].currentTime>0) return vs[j]; }
    return vs.length?vs[0]:null;
  }
  function ensurePill(){
    if (M.pill && M.pill.isConnected) return;
    var p = document.createElement('div');
    p.id='__mono_pill';
    p.textContent='▶ 小窗播放';
    p.setAttribute('style','position:fixed;z-index:2147483647;right:12px;top:76px;background:rgba(10,10,10,.86);color:#fff;padding:8px 14px;border-radius:999px;font-size:13px;line-height:1;font-family:Roboto,-apple-system,sans-serif;cursor:pointer;border:1px solid rgba(255,255,255,.30);box-shadow:0 4px 16px rgba(0,0,0,.30);user-select:none;-webkit-user-select:none;transition:opacity .2s;opacity:0');
    p.addEventListener('click', function(e){ e.preventDefault(); e.stopPropagation(); pop(); }, true);
    (document.body||document.documentElement).appendChild(p);
    M.pill = p;
    setTimeout(function(){ if(M.pill) M.pill.style.opacity='1'; }, 30);
  }
  function pop(){
    var v = activeVideo(); if(!v) return;
    M.video = v;
    try{
      if (v.requestFullscreen) v.requestFullscreen();
      else if (v.webkitRequestFullscreen) v.webkitRequestFullscreen();
      else if (v.webkitEnterFullscreen) v.webkitEnterFullscreen();
      else MonoBridge.popVideo();
    }catch(err){}
  }
  window.__monoPop = pop;
  function onPlay(e){
    M.video = e.target;
    ensurePill(); M.pill.style.opacity='1';
    if (!M.resumed && e.target.readyState>=1){
      M.resumed = true;
      try{
        var rp = parseFloat(MonoBridge.getResume(location.href)||'0');
        if (rp>8 && (!e.target.duration || rp < e.target.duration*0.95)) e.target.currentTime = rp;
      }catch(err){}
    }
  }
  function report(){
    var v = M.video || activeVideo(); if(!v) return;
    try{ MonoBridge.onProgress(location.href, document.title||'', v.currentTime, (isFinite(v.duration)?v.duration:0)); }catch(err){}
  }
  function hook(v){
    if (v.__monoHooked) return; v.__monoHooked = true;
    v.addEventListener('play', onPlay, true);
    v.addEventListener('playing', onPlay, true);
    v.addEventListener('pause', report, true);
    v.addEventListener('ended', report, true);
    if (v.readyState>=3){ ensurePill(); M.pill.style.opacity='1'; }
  }
  function scan(){ qsa('video').forEach(hook); }
  scan();
  try{ new MutationObserver(function(){ scan(); }).observe(document.documentElement,{childList:true,subtree:true,attributeFilter:['src']}); }catch(e){}
  setInterval(function(){ var v=M.video||activeVideo(); if(v && !v.paused) report(); }, 5000);
  // 小窗播放期间，在原视频位置显示占位提示
  window.__monoPinned = function(on){
    var v = M.video || activeVideo(); if(!v) return;
    if (on){
      if (M.ph && M.ph.isConnected) return;
      var d = document.createElement('div');
      d.setAttribute('style','position:fixed;z-index:2147483646;background:rgba(8,8,8,.85);color:#fff;display:flex;align-items:center;justify-content:center;font-size:14px;letter-spacing:2px;font-family:Roboto,sans-serif;');
      d.textContent='视频正在小窗播放 ▶';
      (document.body||document.documentElement).appendChild(d);
      M.ph = d;
      function place(){ try{ var r=v.getBoundingClientRect(); d.style.left=r.left+'px'; d.style.top=r.top+'px'; d.style.width=r.width+'px'; d.style.height=r.height+'px'; }catch(e){} }
      place(); M.phPlace = place;
      window.addEventListener('scroll', place, true);
      window.addEventListener('resize', place, true);
    } else {
      if (M.ph){ M.ph.remove(); M.ph=null; }
      if (M.phPlace){ window.removeEventListener('scroll', M.phPlace, true); window.removeEventListener('resize', M.phPlace, true); M.phPlace=null; }
    }
  };
})();"""

    /** 查询当前主框架视频状态（返回 JSON 或 null） */
    const val STATE =
        """(function(){var v=document.querySelector('video');if(!v)return null;return JSON.stringify({p:v.paused?0:1,c:v.currentTime,d:(isFinite(v.duration)?v.duration:0)});})()"""

    /** 播放/暂停切换 */
    const val TOGGLE =
        """(function(){var v=document.querySelector('video');if(!v)return 0;if(v.paused){try{v.play()}catch(e){}return 1}else{v.pause();return 0}})()"""

    /** 是否全屏元素是视频 */
    const val IS_VIDEO_FS =
        """(function(){var f=document.fullscreenElement||document.webkitFullscreenElement;return (f&&f.tagName==='VIDEO')?1:0;})()"""

    /** 退出网页全屏 */
    const val EXIT_FS =
        """(function(){if(document.exitFullscreen)document.exitFullscreen();else if(document.webkitExitFullscreen)document.webkitExitFullscreen();})()"""

    fun seekJs(sec: Double): String =
        """(function(){var v=document.querySelector('video');if(v&&isFinite(v.duration)){v.currentTime=Math.max(0,Math.min(v.duration,${sec}))}})()"""
}

/** 注入给页面的原生接口（运行在 WebView 的 JavaBridge 线程） */
class MonoBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun popVideo(): String {
        U.runMain { activity.theater.popFromMenu() }
        return "ok"
    }

    @JavascriptInterface
    fun getResume(url: String): String {
        if (!Prefs.resume) return "0"
        val rec = Db.getRecord(url) ?: return "0"
        val pos = rec.position
        if (pos < 8) return "0"
        if (rec.duration > 0 && pos > rec.duration * 0.95) return "0"
        return String.format("%.1f", pos)
    }

    @JavascriptInterface
    fun onProgress(url: String, title: String, pos: Double, dur: Double) {
        if (url.isBlank()) return
        U.runBg { Db.upsertRecord("web", title.ifBlank { url }, url, pos, dur) }
    }
}

/** 解析 STATE 脚本结果 */
data class VideoState(val playing: Boolean, val posSec: Double, val durSec: Double) {
    val hasDuration: Boolean get() = durSec > 0

    companion object {
        fun parse(result: String?): VideoState? {
            if (result == null) return null
            val s = result.trim()
            if (s.isEmpty() || s == "null") return null
            return try {
                val o = JSONObject(s)
                VideoState(o.optInt("p", 0) == 1, o.optDouble("c", 0.0), o.optDouble("d", 0.0))
            } catch (e: Exception) {
                null
            }
        }
    }
}

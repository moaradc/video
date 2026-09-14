package com.moaradc.mono

/** 崩溃信息中转（进程内静态，供诊断页读取） */
object CrashText {
    @Volatile var value: String = "（尚未捕获到崩溃）"
}

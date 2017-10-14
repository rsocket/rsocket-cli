package io.rsocket.cli

import com.google.common.io.CharSource
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader

class SystemInCharSource private constructor() : CharSource() {

    @Throws(IOException::class)
    override fun openStream(): Reader = InputStreamReader(System.`in`)

    companion object {
        val INSTANCE: CharSource = SystemInCharSource()
    }
}

package top.saltfish.ech0.data.net

/** 最近请求诊断：只记录方法、host、路径、状态、耗时和字节数，不记录头/体/Token */
data class RequestRecord(
    val at: Long,
    val method: String,
    val host: String,
    val path: String,
    val status: Int,
    val durationMs: Long,
    val bytes: Int,
    val error: String? = null
)

object RequestDiagnostics {

    private const val MaxRecords = 20
    private val lock = Any()
    private val records = ArrayDeque<RequestRecord>()

    fun record(record: RequestRecord) {
        synchronized(lock) {
            records.addFirst(record)
            while (records.size > MaxRecords) records.removeLast()
        }
    }

    fun recent(): List<RequestRecord> = synchronized(lock) { records.toList() }

    fun clear() {
        synchronized(lock) { records.clear() }
    }
}

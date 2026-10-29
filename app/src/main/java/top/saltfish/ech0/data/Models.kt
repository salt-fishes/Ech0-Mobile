package top.saltfish.ech0.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// 统一响应信封：{code:1成功/0失败, msg, data, error_code, message_key}
@Serializable
data class Envelope<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: T? = null,
    val error_code: String? = null,
    val message_key: String? = null
)

class ApiException(val errorCode: String?, message: String, val httpCode: Int = 0) : Exception(message)

// ---------- 用户 / 认证 ----------

@Serializable
data class User(
    val id: String = "",
    val username: String = "",
    val email: String? = null,
    val is_admin: Boolean = false,
    val is_owner: Boolean = false,
    val avatar: String? = null,
    val locale: String? = null
)

@Serializable
data class TokenPair(
    val access_token: String = "",
    val expires_in: Long = 0
)

@Serializable
data class LoginDto(val username: String, val password: String)

@Serializable
data class InitStatus(val initialized: Boolean = false, val owner_exists: Boolean = false)

// ---------- 站点 / 实例 ----------

@Serializable
data class SiteSettings(
    val site_title: String = "",
    val server_logo: String? = null,
    val server_name: String = "",
    val server_url: String = "",
    val allow_register: Boolean = false,
    val footer_content: String? = null,
    val meting_api: String? = null
)

@Serializable
data class Hello(
    val hello: String = "",
    val version: String = "",
    val commit: String = "",
    val license: String = "",
    val author: String = "",
    val repo_url: String = ""
)

// ---------- 帖子（Echo） ----------

@Serializable
data class FileDto(
    val id: String = "",
    val name: String? = null,
    val key: String = "",
    val storage_type: String = "local",
    val url: String = "",
    val content_type: String? = null,
    val category: String? = null,
    val size: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val user_id: String? = null,
    val created_at: Long = 0
)

@Serializable
data class EchoFile(
    val id: String = "",
    val echo_id: String = "",
    val file_id: String = "",
    val sort_order: Int = 0,
    val file: FileDto? = null
)

@Serializable
data class Tag(
    val id: String = "",
    val name: String = "",
    val usage_count: Int = 0,
    val created_at: Long = 0
)

/** 扩展卡片：type ∈ MUSIC/VIDEO/GITHUBPROJ/WEBSITE/LOCATION/TWEET，payload 结构随 type 变化 */
@Serializable
data class EchoExtension(
    val type: String = "",
    val payload: JsonObject? = null
)

@Serializable
data class Echo(
    val id: String = "",
    val content: String = "",
    val username: String = "",
    val echo_files: List<EchoFile> = emptyList(),
    val layout: String? = null,
    val private: Boolean = false,
    val user_id: String = "",
    val extension: EchoExtension? = null,
    val tags: List<Tag> = emptyList(),
    val fav_count: Int = 0,
    val created_at: Long = 0
)

@Serializable
data class PageResult<T>(
    val items: List<T> = emptyList(),
    val total: Long = 0
)

@Serializable
data class EchoQueryDto(
    val page: Int = 1,
    val pageSize: Int = 20,
    val search: String? = null,
    val tagIds: List<String>? = null,
    val sortBy: String = "created_at",
    val sortOrder: String = "desc",
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val private: Boolean? = null
)

@Serializable
data class EchoFileUpsertDto(val file_id: String, val sort_order: Int = 0)

@Serializable
data class TagUpsertDto(val id: String? = null, val name: String)

@Serializable
data class EchoUpsertDto(
    val id: String? = null,
    val content: String,
    val echo_files: List<EchoFileUpsertDto> = emptyList(),
    val layout: String? = null,
    val private: Boolean = false,
    val extension: EchoExtension? = null,
    val tags: List<TagUpsertDto> = emptyList(),
    val created_at: Long? = null
)

// ---------- 评论 ----------

@Serializable
data class PublicComment(
    val id: String = "",
    val echo_id: String = "",
    val parent_id: String? = null,
    val nickname: String = "",
    val website: String? = null,
    val content: String = "",
    val status: String = "approved",
    val hot: Boolean = false,
    val source: String? = null,
    val created_at: Long = 0,
    val updated_at: Long = 0
)

@Serializable
data class FormMeta(
    val form_token: String = "",
    val min_submit_ms: Long = 0,
    val captcha_enabled: Boolean = false,
    val captcha_api_endpoint: String? = null,
    val enable_comment: Boolean = true
)

@Serializable
data class CreateCommentDto(
    val echo_id: String,
    val parent_id: String? = null,
    val nickname: String? = null,
    val email: String? = null,
    val website: String? = null,
    val content: String,
    val hp_field: String = "",
    val form_token: String,
    val captcha_token: String? = null
)

@Serializable
data class CommentResult(val id: String = "", val status: String = "")

// ---------- 发现 / 统计 ----------

@Serializable
data class HeatmapDay(val date: String = "", val count: Int = 0)

// ---------- Connect / Hub ----------

@Serializable
data class ConnectInfo(
    val server_name: String = "",
    val server_url: String = "",
    val logo: String? = null,
    val total_echos: Long = 0,
    val today_echos: Long = 0,
    val sys_username: String = "",
    val version: String = ""
)

@Serializable
data class ConnectedSite(val id: String = "", val connect_url: String = "")

// ---------- 扩展卡片数据 ----------

@Serializable
data class GithubOwner(
    val login: String = "",
    val avatar_url: String = ""
)

@Serializable
data class GithubRepo(
    val name: String = "",
    val full_name: String = "",
    val description: String? = null,
    val stargazers_count: Int = 0,
    val forks_count: Int = 0,
    val html_url: String = "",
    val owner: GithubOwner = GithubOwner()
)

data class BiliVideo(
    val title: String = "",
    val cover: String = "",
    val owner: String = ""
)

data class TweetEmbed(
    val authorName: String = "",
    val text: String = "",
    val url: String = ""
)

// ---------- 上传 ----------

@Serializable
data class ExternalFileDto(
    val url: String,
    val content_type: String? = null,
    val category: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val name: String? = null
)

package com.nova.os.launcher.sand

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat

class ContactRow(val id: Long, val name: String, val number: String, val lookup: String)

class FileRow(val id: Long, val name: String, val mime: String, val size: Long, val modified: Long, val folder: String)

/** 시스템 설정 바로가기 한 항목. */
class SettingShortcut(val title: String, val keys: String, val action: String)

object SandFinder {
    const val CAT_DOC = 0
    const val CAT_IMAGE = 1
    const val CAT_VIDEO = 2
    const val CAT_AUDIO = 3
    const val CAT_OTHER = 4

    private var contactCache: List<ContactRow> = emptyList()
    private var contactCacheAt = 0L

    private fun granted(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    fun hasContacts(ctx: Context): Boolean = granted(ctx, Manifest.permission.READ_CONTACTS)

    /** 파일 검색 권한: 0 없음, 1 사진·영상·음악만, 2 모든 파일(문서 포함) */
    fun fileAccess(ctx: Context): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) return 2
        } else if (granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return 2
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (granted(ctx, Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(ctx, Manifest.permission.READ_MEDIA_VIDEO) ||
                granted(ctx, Manifest.permission.READ_MEDIA_AUDIO)
            ) return 1
        } else if (Build.VERSION.SDK_INT >= 30 && granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return 1
        }
        return 0
    }

    // ───────────── 연락처 ─────────────

    private fun loadContacts(ctx: Context): List<ContactRow> {
        val out = ArrayList<ContactRow>()
        val seen = HashSet<Long>()
        try {
            val proj = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            )
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(2)
                    if (!seen.add(id)) continue
                    out.add(ContactRow(id, c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(3).orEmpty()))
                }
            }
        } catch (e: Exception) {
            // 권한이 없거나 연락처 앱이 응답하지 않으면 빈 목록
        }
        return out
    }

    /** 백그라운드 스레드에서 호출. 연락처는 1분 동안 메모리에 보관한다. */
    fun contacts(ctx: Context, q: String, limit: Int = 12): List<ContactRow> {
        if (!hasContacts(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        if (now - contactCacheAt > 60_000L || contactCache.isEmpty()) {
            contactCache = loadContacts(ctx)
            contactCacheAt = now
        }
        val query = q.trim()
        val digits = query.filter { it.isDigit() }
        val numberSearch = digits.length >= 3 && digits.length >= query.length - 2
        val out = ArrayList<ContactRow>()
        for (r in contactCache) {
            val hit = SandText.matches(r.name, query) ||
                (numberSearch && r.number.filter { it.isDigit() }.contains(digits))
            if (hit) {
                out.add(r)
                if (out.size >= limit) break
            }
        }
        return out
    }

    fun openContact(ctx: Context, r: ContactRow): Boolean {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, r.lookup)
        return sandStart(ctx, Intent(Intent.ACTION_VIEW, uri))
    }

    fun dial(ctx: Context, number: String): Boolean =
        sandStart(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    fun sms(ctx: Context, number: String): Boolean =
        sandStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + Uri.encode(number))))

    // ───────────── 파일·문서 ─────────────

    private val DocExt = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf",
        "hwp", "hwpx", "odt", "ods", "odp", "epub", "json", "xml", "html"
    )

    fun categoryOf(mime: String, name: String): Int {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in DocExt || mime == "application/pdf" || mime.startsWith("text/") ||
                mime.contains("officedocument") || mime.contains("msword") || mime.contains("hwp") -> CAT_DOC
            mime.startsWith("image/") -> CAT_IMAGE
            mime.startsWith("video/") -> CAT_VIDEO
            mime.startsWith("audio/") -> CAT_AUDIO
            else -> CAT_OTHER
        }
    }

    /** 백그라운드 스레드에서 호출. 이름에 검색어가 들어간 파일을 최근 수정 순으로 찾는다. */
    fun files(ctx: Context, q: String, limit: Int = 60): List<FileRow> {
        if (fileAccess(ctx) == 0) return emptyList()
        val out = ArrayList<FileRow>()
        try {
            val esc = q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val uri = MediaStore.Files.getContentUri("external")
            val proj = ArrayList<String>()
            proj.add(MediaStore.Files.FileColumns._ID)
            proj.add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            proj.add(MediaStore.Files.FileColumns.MIME_TYPE)
            proj.add(MediaStore.Files.FileColumns.SIZE)
            proj.add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= 29) proj.add(MediaStore.MediaColumns.RELATIVE_PATH)
            var sel = MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ? ESCAPE '\\' AND " +
                MediaStore.Files.FileColumns.MIME_TYPE + " IS NOT NULL"
            if (Build.VERSION.SDK_INT >= 29) {
                sel += " AND " + MediaStore.MediaColumns.RELATIVE_PATH + " NOT LIKE 'Android/%'"
            }
            val b = Bundle()
            b.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel)
            b.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%" + esc + "%"))
            b.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
            b.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            b.putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            ctx.contentResolver.query(uri, proj.toTypedArray(), b, null)?.use { c ->
                val hasPath = Build.VERSION.SDK_INT >= 29
                while (c.moveToNext()) {
                    out.add(
                        FileRow(
                            c.getLong(0),
                            c.getString(1).orEmpty(),
                            c.getString(2).orEmpty(),
                            c.getLong(3),
                            c.getLong(4),
                            if (hasPath) c.getString(5).orEmpty() else ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // 저장소 접근이 막혀 있으면 빈 목록
        }
        return out
    }

    fun openFile(ctx: Context, r: FileRow): Boolean {
        val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), r.id)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, if (r.mime.isEmpty()) "*/*" else r.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return sandStart(ctx, i)
    }

    // ───────────── 시스템 설정 바로가기 ─────────────

    val Shortcuts = listOf(
        SettingShortcut("와이파이", "wifi 와이파이 인터넷 무선", Settings.ACTION_WIFI_SETTINGS),
        SettingShortcut("블루투스", "bluetooth 블루투스 연결", Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingShortcut("비행기 모드", "airplane 비행기 모드", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SettingShortcut("디스플레이 · 밝기", "display 밝기 화면 디스플레이 다크", Settings.ACTION_DISPLAY_SETTINGS),
        SettingShortcut("소리 · 진동", "sound 소리 볼륨 진동 벨소리", Settings.ACTION_SOUND_SETTINGS),
        SettingShortcut("배터리", "battery 배터리 절전", Settings.ACTION_BATTERY_SAVER_SETTINGS),
        SettingShortcut("앱 목록 · 권한", "app 앱 권한 애플리케이션", Settings.ACTION_APPLICATION_SETTINGS),
        SettingShortcut("저장공간", "storage 저장공간 용량", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingShortcut("위치", "location 위치 gps", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SettingShortcut("알림 접근", "notification 알림 접근", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        SettingShortcut("접근성", "accessibility 접근성", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingShortcut("기본 앱", "default 기본 앱 홈 런처", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        SettingShortcut("날짜 및 시간", "date time 날짜 시간", Settings.ACTION_DATE_SETTINGS),
        SettingShortcut("언어", "language locale 언어", Settings.ACTION_LOCALE_SETTINGS),
        SettingShortcut("키보드 · 입력 방법", "keyboard input 키보드 입력", Settings.ACTION_INPUT_METHOD_SETTINGS),
        SettingShortcut("NFC", "nfc 엔에프씨 결제", Settings.ACTION_NFC_SETTINGS),
        SettingShortcut("VPN", "vpn 브이피엔", Settings.ACTION_VPN_SETTINGS),
        SettingShortcut("모바일 네트워크 · 데이터", "mobile data 데이터 모바일 네트워크 로밍", Settings.ACTION_WIRELESS_SETTINGS),
        SettingShortcut("개발자 옵션", "developer 개발자 옵션 usb 디버깅", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        SettingShortcut("알람", "alarm 알람 기상", AlarmClock.ACTION_SHOW_ALARMS),
    )

    fun matchShortcuts(q: String): List<SettingShortcut> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        return Shortcuts.filter { s ->
            SandText.matches(s.title, query) || s.keys.contains(query, ignoreCase = true)
        }
    }

    fun openShortcut(ctx: Context, s: SettingShortcut): Boolean = sandStart(ctx, Intent(s.action))
}

package com.simplemobiletools.contacts.pro.extensions

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.content.FileProvider
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.PERMISSION_WRITE_CONTACTS
import com.simplemobiletools.contacts.pro.BuildConfig
import com.simplemobiletools.contacts.pro.R
import com.simplemobiletools.contacts.pro.activities.EditContactActivity
import com.simplemobiletools.contacts.pro.activities.ViewContactActivity
import com.simplemobiletools.contacts.pro.databases.ContactsDatabase
import com.simplemobiletools.contacts.pro.helpers.*
import com.simplemobiletools.contacts.pro.interfaces.ContactsDao
import com.simplemobiletools.contacts.pro.interfaces.GroupsDao
import com.simplemobiletools.contacts.pro.models.Contact
import com.simplemobiletools.contacts.pro.models.ContactSource
import com.simplemobiletools.contacts.pro.models.Organization
import java.io.File


val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.contactsDB: ContactsDao get() = ContactsDatabase.getInstance(applicationContext).ContactsDao()

val Context.groupsDB: GroupsDao get() = ContactsDatabase.getInstance(applicationContext).GroupsDao()

val Context.telecomManager: TelecomManager get() = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

fun Context.getEmptyContact(): Contact {
    val originalContactSource = if (hasContactPermissions()) config.lastUsedContactSource else SMT_PRIVATE
    val organization = Organization("", "")
    return Contact(0, "", "", "", "", "", "", "", ArrayList(), ArrayList(), ArrayList(), ArrayList(), originalContactSource, 0, 0, "",
            null, "", ArrayList(), organization, ArrayList(), ArrayList())
}

fun Context.viewContact(contact: Contact) {
    Intent(applicationContext, ViewContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.source == SMT_PRIVATE)
        startActivity(this)
    }
}

fun Context.editContact(contact: Contact) {
    Intent(applicationContext, EditContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.source == SMT_PRIVATE)
        startActivity(this)
    }
}

fun Context.sendEmailIntent(recipient: String) {
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.fromParts("mailto", recipient, null)
        if (resolveActivity(packageManager) != null) {
            startActivity(this)
        } else {
            toast(R.string.no_app_found)
        }
    }
}

fun Context.sendSMSIntent(recipient: String) {
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.fromParts("smsto", recipient, null)
        if (resolveActivity(packageManager) != null) {
            startActivity(this)
        } else {
            toast(R.string.no_app_found)
        }
    }
}

fun Context.sendAddressIntent(address: String) {
    val location = Uri.encode(address)
    val uri = Uri.parse("geo:0,0?q=$location")

    Intent(Intent.ACTION_VIEW, uri).apply {
        if (resolveActivity(packageManager) != null) {
            startActivity(this)
        } else {
            toast(R.string.no_app_found)
        }
    }
}

fun Context.openWebsiteIntent(url: String) {
    Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(url)
        if (resolveActivity(packageManager) != null) {
            startActivity(this)
        } else {
            toast(R.string.no_app_found)
        }
    }
}

fun Context.getLookupUriRawId(dataUri: Uri): Int {
    val lookupKey = getLookupKeyFromUri(dataUri)
    if (lookupKey != null) {
        val uri = lookupContactUri(lookupKey, this)
        if (uri != null) {
            return getContactUriRawId(uri)
        }
    }
    return -1
}

fun Context.getContactUriRawId(uri: Uri): Int {
    val projection = arrayOf(ContactsContract.Contacts.NAME_RAW_CONTACT_ID)
    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, null, null, null)
        if (cursor.moveToFirst()) {
            return cursor.getIntValue(ContactsContract.Contacts.NAME_RAW_CONTACT_ID)
        }
    } catch (ignored: Exception) {
    } finally {
        cursor?.close()
    }
    return -1
}

// from https://android.googlesource.com/platform/packages/apps/Dialer/+/68038172793ee0e2ab3e2e56ddfbeb82879d1f58/java/com/android/contacts/common/util/UriUtils.java
fun getLookupKeyFromUri(lookupUri: Uri): String? {
    return if (!isEncodedContactUri(lookupUri)) {
        val segments = lookupUri.pathSegments
        if (segments.size < 3) null else Uri.encode(segments[2])
    } else {
        null
    }
}

fun isEncodedContactUri(uri: Uri?): Boolean {
    if (uri == null) {
        return false
    }
    val lastPathSegment = uri.lastPathSegment ?: return false
    return lastPathSegment == "encoded"
}

fun lookupContactUri(lookup: String, context: Context): Uri? {
    val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookup)
    return try {
        ContactsContract.Contacts.lookupContact(context.contentResolver, lookupUri)
    } catch (e: Exception) {
        null
    }
}

fun Context.getCachePhoto(): File {
    val imagesFolder = File(cacheDir, "my_cache")
    if (!imagesFolder.exists()) {
        imagesFolder.mkdirs()
    }

    val file = File(imagesFolder, "Photo_${System.currentTimeMillis()}.jpg")
    file.createNewFile()
    return file
}

fun Context.getCachePhotoUri(file: File = getCachePhoto()) = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", file)

fun Context.getPhotoThumbnailSize(): Int {
    val uri = ContactsContract.DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI
    val projection = arrayOf(ContactsContract.DisplayPhoto.THUMBNAIL_MAX_DIM)
    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, null, null, null)
        if (cursor?.moveToFirst() == true) {
            return cursor.getIntValue(ContactsContract.DisplayPhoto.THUMBNAIL_MAX_DIM)
        }
    } catch (ignored: Exception) {
    } finally {
        cursor?.close()
    }
    return 0
}

fun Context.hasContactPermissions() = hasPermission(PERMISSION_READ_CONTACTS) && hasPermission(PERMISSION_WRITE_CONTACTS)

fun Context.getPublicContactSource(source: String): String {
    return when (source) {
        config.localAccountName -> getString(R.string.phone_storage)
        SMT_PRIVATE -> getString(R.string.phone_storage_hidden)
        else -> source
    }
}

fun Context.sendSMSToContacts(contacts: ArrayList<Contact>) {
    val numbers = StringBuilder()
    contacts.forEach {
        val number = it.phoneNumbers.firstOrNull { it.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE }
                ?: it.phoneNumbers.firstOrNull()
        if (number != null) {
            numbers.append("${number.value};")
        }

        val uriString = "smsto:${numbers.toString().trimEnd(';')}"
        Intent(Intent.ACTION_SENDTO, Uri.parse(uriString)).apply {
            if (resolveActivity(packageManager) != null) {
                startActivity(this)
            } else {
                toast(R.string.no_app_found)
            }
        }
    }
}

fun Context.sendEmailToContacts(contacts: ArrayList<Contact>) {
    val emails = ArrayList<String>()
    contacts.forEach {
        it.emails.forEach {
            if (it.value.isNotEmpty()) {
                emails.add(it.value)
            }
        }
    }

    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray())
        if (resolveActivity(packageManager) != null) {
            startActivity(this)
        } else {
            toast(R.string.no_app_found)
        }
    }
}

fun Context.getTempFile(): File? {
    val folder = File(cacheDir, "contacts")
    if (!folder.exists()) {
        if (!folder.mkdir()) {
            toast(R.string.unknown_error_occurred)
            return null
        }
    }

    return File(folder, "contacts.vcf")
}

fun Context.addContactsToGroup(contacts: ArrayList<Contact>, groupId: Long) {
    val publicContacts = contacts.filter { it.source != SMT_PRIVATE }.toMutableList() as ArrayList<Contact>
    val privateContacts = contacts.filter { it.source == SMT_PRIVATE }.toMutableList() as ArrayList<Contact>
    if (publicContacts.isNotEmpty()) {
        ContactsHelper(this).addContactsToGroup(publicContacts, groupId)
    }

    if (privateContacts.isNotEmpty()) {
        LocalContactsHelper(this).addContactsToGroup(privateContacts, groupId)
    }
}

fun Context.removeContactsFromGroup(contacts: ArrayList<Contact>, groupId: Long) {
    val publicContacts = contacts.filter { it.source != SMT_PRIVATE }.toMutableList() as ArrayList<Contact>
    val privateContacts = contacts.filter { it.source == SMT_PRIVATE }.toMutableList() as ArrayList<Contact>
    if (publicContacts.isNotEmpty() && hasContactPermissions()) {
        ContactsHelper(this).removeContactsFromGroup(publicContacts, groupId)
    }

    if (privateContacts.isNotEmpty()) {
        LocalContactsHelper(this).removeContactsFromGroup(privateContacts, groupId)
    }
}

fun Context.getContactPublicUri(contact: Contact): Uri {
    val lookupKey = ContactsHelper(this).getContactLookupKey(contact.id.toString())
    return Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
}

fun Context.getVisibleContactSources(): ArrayList<String> {
    val sources = ContactsHelper(this).getDeviceContactSources()
    sources.add(ContactSource(getString(R.string.phone_storage_hidden), SMT_PRIVATE))
    val sourceNames = ArrayList(sources).map { if (it.type == SMT_PRIVATE) SMT_PRIVATE else it.name }.toMutableList() as ArrayList<String>
    sourceNames.removeAll(config.ignoredContactSources)
    return sourceNames
}

fun Context.addBlockedNumber(number: String) {
    ContentValues().apply {
        put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        contentResolver.insert(BlockedNumbers.CONTENT_URI, this)
    }
}

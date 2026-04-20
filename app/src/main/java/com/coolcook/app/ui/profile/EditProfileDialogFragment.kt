package com.coolcook.app.ui.profile

import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.coolcook.app.BuildConfig
import com.coolcook.app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditProfileDialogFragment : DialogFragment() {

    private lateinit var imgAvatar: ShapeableImageView
    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilBirthDate: TextInputLayout
    private lateinit var edtFullName: TextInputEditText
    private lateinit var edtPhone: TextInputEditText
    private lateinit var edtBirthDate: TextInputEditText
    private lateinit var btnSaveProfile: MaterialButton

    private val dateFormatter = SimpleDateFormat(DATE_PATTERN, Locale.forLanguageTag("vi-VN"))
    private var selectedAvatarUri: Uri? = null
    private var existingAvatarUrl: String = ""
    private var isSaving = false

    private val pickAvatarMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            imgAvatar.setImageURI(uri)
        }
    }

    override fun getTheme(): Int = R.style.ThemeOverlay_Coolcook_EditProfileDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), theme).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imgAvatar = view.findViewById(R.id.imgEditProfileAvatar)
        tilFullName = view.findViewById(R.id.tilFullName)
        tilPhone = view.findViewById(R.id.tilPhone)
        tilBirthDate = view.findViewById(R.id.tilBirthDate)
        edtFullName = view.findViewById(R.id.edtFullName)
        edtPhone = view.findViewById(R.id.edtPhone)
        edtBirthDate = view.findViewById(R.id.edtBirthDate)
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile)

        edtFullName.setText(arguments?.getString(ARG_FULL_NAME).orEmpty())
        edtPhone.setText(arguments?.getString(ARG_PHONE_NUMBER).orEmpty())
        edtBirthDate.setText(arguments?.getString(ARG_BIRTH_DATE).orEmpty())

        existingAvatarUrl = arguments?.getString(ARG_AVATAR_URL).orEmpty()
        if (existingAvatarUrl.isNotBlank()) {
            Glide.with(this)
                .load(existingAvatarUrl)
                .placeholder(R.drawable.img_home_profile)
                .error(R.drawable.img_home_profile)
                .into(imgAvatar)
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser?.displayName?.isNotBlank() == true && edtFullName.text.isNullOrBlank()) {
            edtFullName.setText(firebaseUser.displayName)
        }

        view.findViewById<View>(R.id.btnCloseDialog).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnAvatarCamera).setOnClickListener { openImagePicker() }
        imgAvatar.setOnClickListener { openImagePicker() }

        edtBirthDate.setOnClickListener { showDatePicker() }
        tilBirthDate.setOnClickListener { showDatePicker() }

        view.findViewById<View>(R.id.btnSaveProfile).setOnClickListener { onSaveClicked() }
    }

    private fun openImagePicker() {
        pickAvatarMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val existingDate = edtBirthDate.text?.toString()?.trim().orEmpty()

        if (existingDate.isNotEmpty()) {
            runCatching {
                dateFormatter.parse(existingDate)
            }.getOrNull()?.let { parsed ->
                calendar.time = parsed
            }
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                edtBirthDate.setText(dateFormatter.format(calendar.time))
                tilBirthDate.error = null
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun onSaveClicked() {
        if (isSaving) {
            return
        }

        val fullName = edtFullName.text?.toString()?.trim().orEmpty()
        val rawPhone = edtPhone.text?.toString()?.trim().orEmpty()
        val phone = rawPhone.replace(" ", "")
        val birthDate = edtBirthDate.text?.toString()?.trim().orEmpty()

        tilFullName.error = null
        tilPhone.error = null
        tilBirthDate.error = null

        var isValid = true

        if (fullName.isEmpty()) {
            tilFullName.error = getString(R.string.profile_edit_error_required)
            isValid = false
        }

        if (phone.isEmpty()) {
            tilPhone.error = getString(R.string.profile_edit_error_required)
            isValid = false
        } else if (!phone.all { it.isDigit() }) {
            tilPhone.error = getString(R.string.profile_edit_error_phone_invalid)
            isValid = false
        }

        if (birthDate.isEmpty()) {
            tilBirthDate.error = getString(R.string.profile_edit_error_required)
            isValid = false
        }

        if (!isValid) {
            return
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            Toast.makeText(requireContext(), R.string.profile_edit_user_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setSavingState(true)

        val saveProfile: (String) -> Unit = { avatarUrl ->
            saveProfileToFirestore(
                uid = firebaseUser.uid,
                fullName = fullName,
                phone = phone,
                birthDate = birthDate,
                avatarUrl = avatarUrl
            )
        }

        val avatarUri = selectedAvatarUri
        if (avatarUri != null) {
            uploadAvatarToCloudinary(
                avatarUri = avatarUri,
                onSuccess = saveProfile,
                onFailure = {
                    setSavingState(false)
                    Toast.makeText(requireContext(), R.string.profile_edit_upload_failed, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            saveProfile(existingAvatarUrl)
        }
    }

    private fun setSavingState(isSaving: Boolean) {
        this.isSaving = isSaving
        btnSaveProfile.isEnabled = !isSaving
        btnSaveProfile.text = getString(
            if (isSaving) R.string.profile_edit_saving else R.string.profile_edit_save
        )
    }

    private fun uploadAvatarToCloudinary(
        avatarUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit
    ) {
        if (!ensureCloudinaryConfigured()) {
            Toast.makeText(requireContext(), R.string.profile_edit_cloudinary_missing, Toast.LENGTH_SHORT).show()
            onFailure()
            return
        }

        // Đọc trực tiếp URI sang mảng byte[] để tránh hoàn toàn các lỗi về quyền truy cập file / URI của WorkManager
        val bytes = try {
            requireContext().contentResolver.openInputStream(avatarUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đọc file ảnh", e)
            null
        }

        if (bytes == null) {
            Log.e(TAG, "Không thể đọc dữ liệu từ URI")
            onFailure()
            return
        }

        MediaManager.get().upload(bytes)
            .option("resource_type", "image")
            .option("folder", "coolcook/profile")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {
                    Log.d(TAG, "Cloudinary upload start: $requestId")
                }

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                    // No-op
                }

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    runOnUiThreadSafely {
                        val secureUrl = resultData?.get("secure_url")?.toString().orEmpty()
                        if (secureUrl.isBlank()) {
                            Log.e(TAG, "Cloudinary trả về secure_url rỗng")
                            onFailure()
                            return@runOnUiThreadSafely
                        }
                        onSuccess(secureUrl)
                    }
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    Log.e(TAG, "Cloudinary upload error: ${error?.description}")
                    runOnUiThreadSafely { onFailure() }
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                    Log.w(TAG, "Cloudinary upload rescheduled: ${error?.description}")
                }
            })
            .dispatch()
    }

    private fun runOnUiThreadSafely(action: () -> Unit) {
        if (isAdded) {
            requireActivity().runOnUiThread(action)
        }
    }

    private fun ensureCloudinaryConfigured(): Boolean {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
        val apiKey = BuildConfig.CLOUDINARY_API_KEY.trim()
        val apiSecret = BuildConfig.CLOUDINARY_API_SECRET.trim()

        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return false
        }

        return try {
            MediaManager.get()
            true
        } catch (_: IllegalStateException) {
            val config = hashMapOf(
                "cloud_name" to cloudName,
                "api_key" to apiKey,
                "api_secret" to apiSecret,
                "secure" to true
            )
            MediaManager.init(requireContext().applicationContext, config)
            true
        }
    }

    private fun saveProfileToFirestore(
        uid: String,
        fullName: String,
        phone: String,
        birthDate: String,
        avatarUrl: String
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val payload = hashMapOf<String, Any>(
            "fullName" to fullName,
            "phoneNumber" to phone,
            "birthDate" to birthDate,
            "email" to (currentUser?.email ?: ""),
            "avatarUrl" to avatarUrl,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection(FIRESTORE_COLLECTION)
            .document(uid)
            .set(payload, SetOptions.merge())
            .addOnSuccessListener {
                existingAvatarUrl = avatarUrl

                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    Bundle().apply {
                        putString(RESULT_FULL_NAME, fullName)
                        putString(RESULT_PHONE_NUMBER, phone)
                        putString(RESULT_BIRTH_DATE, birthDate)
                        putString(RESULT_AVATAR_URL, avatarUrl)
                    }
                )

                Toast.makeText(requireContext(), R.string.profile_edit_saved_success, Toast.LENGTH_SHORT).show()
                dismissAllowingStateLoss()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Luu profile Firestore that bai", error)
                setSavingState(false)
                Toast.makeText(requireContext(), R.string.profile_edit_save_failed, Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        private const val ARG_FULL_NAME = "arg_full_name"
        private const val ARG_PHONE_NUMBER = "arg_phone_number"
        private const val ARG_BIRTH_DATE = "arg_birth_date"
        private const val ARG_AVATAR_URL = "arg_avatar_url"

        private const val RESULT_FULL_NAME = "result_full_name"
        private const val RESULT_PHONE_NUMBER = "result_phone_number"
        private const val RESULT_BIRTH_DATE = "result_birth_date"
        private const val RESULT_AVATAR_URL = "result_avatar_url"

        private const val RESULT_KEY = "edit_profile_result"
        private const val FIRESTORE_COLLECTION = "users"
        private const val TAG = "EditProfileDialog"
        private const val DATE_PATTERN = "dd/MM/yyyy"

        @JvmStatic
        fun newInstance(
            fullName: String,
            phoneNumber: String,
            birthDate: String,
            avatarUrl: String
        ): EditProfileDialogFragment {
            return EditProfileDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FULL_NAME, fullName)
                    putString(ARG_PHONE_NUMBER, phoneNumber)
                    putString(ARG_BIRTH_DATE, birthDate)
                    putString(ARG_AVATAR_URL, avatarUrl)
                }
            }
        }
    }
}

package com.coolcook.app.ui.profile

import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.coolcook.app.BuildConfig
import com.coolcook.app.R
import com.coolcook.app.util.AvatarImageUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class EditProfileDialogFragment : DialogFragment() {

    private lateinit var imgAvatar: ShapeableImageView
    private lateinit var btnCloseDialog: View
    private lateinit var btnAvatarCamera: View
    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilBirthDate: TextInputLayout
    private lateinit var edtFullName: TextInputEditText
    private lateinit var edtPhone: TextInputEditText
    private lateinit var edtBirthDate: TextInputEditText
    private lateinit var btnSaveProfile: MaterialButton
    private lateinit var avatarUploadStateContainer: View
    private lateinit var progressAvatarUpload: LinearProgressIndicator
    private lateinit var txtAvatarUploadState: TextView

    private val dateFormatter = SimpleDateFormat(DATE_PATTERN, Locale.forLanguageTag("vi-VN"))
    private var selectedAvatarUri: Uri? = null
    private var existingAvatarUrl: String = ""
    private var isSaving = false

    private val pickAvatarMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            renderLocalAvatarPreview(uri)
            hideAvatarUploadState()
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
        btnCloseDialog = view.findViewById(R.id.btnCloseDialog)
        btnAvatarCamera = view.findViewById(R.id.btnAvatarCamera)
        tilFullName = view.findViewById(R.id.tilFullName)
        tilPhone = view.findViewById(R.id.tilPhone)
        tilBirthDate = view.findViewById(R.id.tilBirthDate)
        edtFullName = view.findViewById(R.id.edtFullName)
        edtPhone = view.findViewById(R.id.edtPhone)
        edtBirthDate = view.findViewById(R.id.edtBirthDate)
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile)
        avatarUploadStateContainer = view.findViewById(R.id.avatarUploadStateContainer)
        progressAvatarUpload = view.findViewById(R.id.progressAvatarUpload)
        txtAvatarUploadState = view.findViewById(R.id.txtAvatarUploadState)

        edtFullName.setText(arguments?.getString(ARG_FULL_NAME).orEmpty())
        edtPhone.setText(arguments?.getString(ARG_PHONE_NUMBER).orEmpty())
        edtBirthDate.setText(arguments?.getString(ARG_BIRTH_DATE).orEmpty())

        existingAvatarUrl = arguments?.getString(ARG_AVATAR_URL).orEmpty()
        if (existingAvatarUrl.isNotBlank()) {
            renderRemoteAvatar(existingAvatarUrl)
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser?.displayName?.isNotBlank() == true && edtFullName.text.isNullOrBlank()) {
            edtFullName.setText(firebaseUser.displayName)
        }

        btnCloseDialog.setOnClickListener { dismiss() }
        btnAvatarCamera.setOnClickListener { openImagePicker() }
        imgAvatar.setOnClickListener { openImagePicker() }

        edtBirthDate.setOnClickListener { showDatePicker() }
        tilBirthDate.setOnClickListener { showDatePicker() }

        btnSaveProfile.setOnClickListener { onSaveClicked() }
    }

    private fun openImagePicker() {
        pickAvatarMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun renderLocalAvatarPreview(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.img_home_profile)
            .error(R.drawable.img_home_profile)
            .into(imgAvatar)
    }

    private fun renderRemoteAvatar(avatarUrl: String) {
        val optimizedAvatarUrl = AvatarImageUtils.buildOptimizedAvatarUrl(
            avatarUrl,
            resources.getDimensionPixelSize(R.dimen.profile_edit_avatar_size)
        )

        Glide.with(this)
            .load(optimizedAvatarUrl)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.drawable.img_home_profile)
            .error(R.drawable.img_home_profile)
            .into(imgAvatar)
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
        btnCloseDialog.isEnabled = !isSaving
        btnAvatarCamera.isEnabled = !isSaving
        imgAvatar.isEnabled = !isSaving
        edtFullName.isEnabled = !isSaving
        edtPhone.isEnabled = !isSaving
        edtBirthDate.isEnabled = !isSaving
        btnSaveProfile.text = getString(
            if (isSaving) R.string.profile_edit_saving else R.string.profile_edit_save
        )

        if (!isSaving) {
            hideAvatarUploadState()
        }
    }

    private fun showAvatarUploadState(message: String, progress: Int? = null) {
        avatarUploadStateContainer.visibility = View.VISIBLE
        txtAvatarUploadState.text = message

        if (progress == null) {
            progressAvatarUpload.isIndeterminate = true
        } else {
            progressAvatarUpload.isIndeterminate = false
            progressAvatarUpload.setProgressCompat(progress.coerceIn(0, 100), true)
        }
    }

    private fun hideAvatarUploadState() {
        avatarUploadStateContainer.visibility = View.GONE
        progressAvatarUpload.isIndeterminate = true
        progressAvatarUpload.progress = 0
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

        showAvatarUploadState(getString(R.string.profile_edit_preparing_image))

        Thread {
            val preparedBytes = prepareAvatarForUpload(avatarUri)
            if (preparedBytes == null) {
                Log.e(TAG, "Unable to prepare avatar before upload")
                runOnUiThreadSafely { onFailure() }
                return@Thread
            }

            val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
            val uploadRequest = MediaManager.get().upload(preparedBytes)
                .option("resource_type", "image")

            if (uploadPreset.isNotBlank()) {
                uploadRequest.option("upload_preset", uploadPreset)
            } else {
                uploadRequest.option("folder", "coolcook/profile")
            }

            uploadRequest
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {
                        Log.d(TAG, "Cloudinary upload start: $requestId")
                        runOnUiThreadSafely {
                            showAvatarUploadState(getString(R.string.profile_edit_uploading_image))
                        }
                    }

                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                        runOnUiThreadSafely {
                            if (totalBytes <= 0L) {
                                showAvatarUploadState(getString(R.string.profile_edit_uploading_image))
                                return@runOnUiThreadSafely
                            }

                            val progress = ((bytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            showAvatarUploadState(
                                getString(R.string.profile_edit_uploading_image_progress, progress),
                                progress
                            )
                        }
                    }

                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        runOnUiThreadSafely {
                            val secureUrl = resultData?.get("secure_url")?.toString().orEmpty()
                            if (secureUrl.isBlank()) {
                                Log.e(TAG, "Cloudinary returned an empty secure_url")
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
        }.start()
    }

    private fun prepareAvatarForUpload(avatarUri: Uri): ByteArray? {
        return try {
            val source = ImageDecoder.createSource(requireContext().contentResolver, avatarUri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false

                val sourceWidth = info.size.width.coerceAtLeast(1)
                val sourceHeight = info.size.height.coerceAtLeast(1)
                val largestSide = max(sourceWidth, sourceHeight)

                if (largestSide > AVATAR_UPLOAD_MAX_DIMENSION_PX) {
                    val scale = AVATAR_UPLOAD_MAX_DIMENSION_PX.toFloat() / largestSide.toFloat()
                    decoder.setTargetSize(
                        (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                        (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }

            ByteArrayOutputStream().use { outputStream ->
                val didCompress = bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    AVATAR_UPLOAD_QUALITY,
                    outputStream
                )
                bitmap.recycle()

                if (!didCompress) {
                    return null
                }

                outputStream.toByteArray()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Avatar optimization failed", error)
            null
        }
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
        val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
        val hasSignedCredentials = apiKey.isNotBlank() && apiSecret.isNotBlank()
        val hasUnsignedPreset = uploadPreset.isNotBlank()

        if (cloudName.isBlank() || (!hasSignedCredentials && !hasUnsignedPreset)) {
            return false
        }

        return try {
            MediaManager.get()
            true
        } catch (_: IllegalStateException) {
            val config = hashMapOf<String, Any>(
                "cloud_name" to cloudName,
                "secure" to true
            )
            if (apiKey.isNotBlank()) {
                config["api_key"] = apiKey
            }
            if (apiSecret.isNotBlank()) {
                config["api_secret"] = apiSecret
            }
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
                selectedAvatarUri = null

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
                Log.e(TAG, "Failed to save profile to Firestore", error)
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
        private const val AVATAR_UPLOAD_MAX_DIMENSION_PX = 960
        private const val AVATAR_UPLOAD_QUALITY = 82

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

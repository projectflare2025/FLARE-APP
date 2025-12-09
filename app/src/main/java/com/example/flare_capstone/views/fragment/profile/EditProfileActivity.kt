package com.example.flare_capstone.views.fragment.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.flare_capstone.R
import com.example.flare_capstone.utils.ThemeManager
import com.example.flare_capstone.databinding.ActivityEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val CAMERA_REQUEST_CODE = 101
        private const val CAMERA_PERMISSION_REQUEST_CODE = 102
        private const val GALLERY_REQUEST_CODE = 104
        private const val GALLERY_PERMISSION_REQUEST_CODE = 103
    }

    private var base64ProfileImage: String? = null
    private var hasProfileImage = false
    private var removeProfileImageRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.back.setOnClickListener { finish() }

        binding.profileIcon.setOnClickListener { showImageSourceSheet() }
        binding.changePhotoIcon.setOnClickListener { showImageSourceSheet() }

        binding.email.isEnabled = false

        loadUserData()

        binding.saveButton.setOnClickListener { saveChanges() }
    }

    /* ============================================================
       LOAD USER DATA FROM FIRESTORE
       ============================================================ */
    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                binding.name.setText(doc.getString("name") ?: "")
                binding.email.setText(doc.getString("email") ?: "")
                binding.contact.setText(doc.getString("contact") ?: "")

                val profileBase64 = doc.getString("profile")
                val bmp = convertBase64ToBitmap(profileBase64)
                if (bmp != null) {
                    binding.profileIcon.setImageBitmap(bmp)
                    hasProfileImage = true
                    base64ProfileImage = profileBase64
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
            }
    }

    /* ============================================================
       SAVE CHANGES TO FIRESTORE
       ============================================================ */
    private fun saveChanges() {
        val userId = auth.currentUser?.uid ?: return

        val newName = binding.name.text.toString().trim()
        var newContact = binding.contact.text.toString().trim()
        val email = binding.email.text.toString().trim()

        if (newName.isEmpty()) {
            binding.name.error = "Required"
            return
        }

        if (newContact.startsWith("639")) {
            newContact = newContact.replaceFirst("639", "09")
            binding.contact.setText(newContact)
        }

        if (!newContact.matches(Regex("^09\\d{9}$"))) {
            binding.contact.error = "Invalid contact number"
            return
        }

        // Check if contact number already exists
        firestore.collection("Users")
            .whereEqualTo("contact", newContact)
            .get()
            .addOnSuccessListener { query ->
                var conflict = false
                for (doc in query.documents) {
                    if (doc.id != userId) {
                        conflict = true
                        break
                    }
                }

                if (conflict) {
                    binding.contact.error = "Contact number already used"
                    return@addOnSuccessListener
                }

                // Proceed to save
                updateUserData(userId, newName, email, newContact)
            }
    }

    private fun updateUserData(
        userId: String,
        name: String,
        email: String,
        contact: String
    ) {
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "email" to email,
            "contact" to contact
        )

        if (base64ProfileImage != null && !removeProfileImageRequested)
            updates["profile"] = base64ProfileImage!!

        firestore.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener {
                if (removeProfileImageRequested) {
                    firestore.collection("users").document(userId)
                        .update("profile", null)
                }
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    /* ============================================================
       IMAGE PICKER
       ============================================================ */
    private fun showImageSourceSheet() {
        val options = if (hasProfileImage)
            arrayOf("Take photo", "Choose from gallery", "Remove photo")
        else
            arrayOf("Take photo", "Choose from gallery")

        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Take photo" -> ensureCameraPermission()
                    "Choose from gallery" -> ensureGalleryPermission()
                    "Remove photo" -> {
                        binding.profileIcon.setImageResource(R.drawable.ic_profile)
                        base64ProfileImage = null
                        hasProfileImage = false
                        removeProfileImageRequested = true
                    }
                }
            }
            .show()
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else openCamera()
    }

    private fun ensureGalleryPermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(perm),
                GALLERY_PERMISSION_REQUEST_CODE
            )
        } else openGallery()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_REQUEST_CODE)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                val bmp = data?.extras?.get("data") as? Bitmap ?: return
                binding.profileIcon.setImageBitmap(bmp)
                base64ProfileImage = convertBitmapToBase64(bmp)
                hasProfileImage = true
                removeProfileImageRequested = false
            }

            GALLERY_REQUEST_CODE -> {
                val uri = data?.data ?: return
                val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                binding.profileIcon.setImageBitmap(bmp)
                base64ProfileImage = convertBitmapToBase64(bmp)
                hasProfileImage = true
                removeProfileImageRequested = false
            }
        }
    }

    /* ============================================================
       BITMAP <-> BASE64
       ============================================================ */
    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        return Base64.encodeToString(output.toByteArray(), Base64.DEFAULT)
    }

    private fun convertBase64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrEmpty()) return null
        return try {
            val decoded = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        } catch (e: Exception) {
            null
        }
    }
}
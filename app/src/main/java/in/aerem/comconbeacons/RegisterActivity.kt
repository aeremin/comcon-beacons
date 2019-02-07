package `in`.aerem.comconbeacons

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import `in`.aerem.comconbeacons.models.LoginResponse
import `in`.aerem.comconbeacons.models.RegisterRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import java.io.IOException

class RegisterActivity : AppCompatActivity() {
    private val TAG = "ComConBeacons"

    // Keep track of the login task to ensure we can cancel it if requested.
    private var mRegisterTask: UserRegisterTask? = null

    // UI references.
    private lateinit var mEmailView: AutoCompleteTextView
    private lateinit var mNameView: EditText
    private lateinit var mPasswordView: EditText
    private lateinit var mPasswordRepeatView: EditText
    private lateinit var mProgressView: View
    private lateinit var mLoginFormView: View

    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Set up the login form.
        mEmailView = findViewById(R.id.email)
        mNameView = findViewById(R.id.name)
        mPasswordView = findViewById(R.id.password)
        mPasswordRepeatView = findViewById(R.id.password_repeat)
        mPasswordRepeatView.setOnEditorActionListener(TextView.OnEditorActionListener { textView, id, keyEvent ->
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                attemptRegister()
                return@OnEditorActionListener true
            }
            false
        })

        val registerButton = findViewById<Button>(R.id.email_register_button)
        registerButton.setOnClickListener { attemptRegister() }

        mLoginFormView = findViewById(R.id.login_form)
        mProgressView = findViewById(R.id.login_progress)

        mSharedPreferences = (application as ComConBeaconsApplication).getGlobalSharedPreferences()
    }

    private fun attemptRegister() {
        if (mRegisterTask != null) {
            return
        }
        val formData = getRegisterFormData()
        if (formData != null) {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true)
            mRegisterTask = UserRegisterTask(formData)
            mRegisterTask!!.execute(null as Void?)
        }
    }

    private inner class RegisterFormData(var email: String, var name: String, var password: String)

    private fun getRegisterFormData(): RegisterFormData? {
        mEmailView.error = null
        mNameView.error = null
        mPasswordView.error = null
        mPasswordRepeatView.error = null

        val email = mEmailView.text.toString()
        val name = mNameView.text.toString()
        val password = mPasswordView.text.toString()
        val passwordRepeat = mPasswordRepeatView.text.toString()
        if (TextUtils.isEmpty(email)) {
            mEmailView.error = getString(R.string.error_field_required)
            mEmailView.requestFocus()
            return null
        }
        if (TextUtils.isEmpty(name)) {
            mNameView.error = getString(R.string.error_field_required)
            mNameView.requestFocus()
            return null
        }
        if (TextUtils.isEmpty(password)) {
            mPasswordView.error = getString(R.string.error_empty_password)
            mPasswordView.requestFocus()
            return null
        }
        if (TextUtils.isEmpty(passwordRepeat)) {
            mPasswordRepeatView.error = getString(R.string.error_empty_password)
            mPasswordRepeatView.requestFocus()
            return null
        }
        if (!TextUtils.equals(password, passwordRepeat)) {
            mPasswordRepeatView.error = getString(R.string.error_password_not_repeated)
            mPasswordRepeatView.requestFocus()
            return null
        }

        return RegisterFormData(email, name, password)
    }

    private fun showProgress(show: Boolean) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

        mLoginFormView.visibility = if (show) View.GONE else View.VISIBLE
        mLoginFormView.animate().setDuration(shortAnimTime.toLong()).alpha(
            (if (show) 0 else 1).toFloat()
        ).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                mLoginFormView.visibility = if (show) View.GONE else View.VISIBLE
            }
        })

        mProgressView.visibility = if (show) View.VISIBLE else View.GONE
        mProgressView.animate().setDuration(shortAnimTime.toLong()).alpha(
            (if (show) 1 else 0).toFloat()
        ).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                mProgressView.visibility = if (show) View.VISIBLE else View.GONE
            }
        })
    }

    protected fun onSuccessfulRegister(token: String) {
        Log.i(TAG, "Successful registration, token = $token")
        val editor = mSharedPreferences.edit()
        editor.putString(getString(R.string.token_preference_key), token)
        editor.commit()

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private inner class UserRegisterTask internal constructor(mFormData: RegisterFormData) :
        AsyncTask<Void, Void, String>() {
        protected val mRegisterRequest: RegisterRequest
        internal var mService: PositionsWebService

        init {
            mRegisterRequest = RegisterRequest(mFormData.email, mFormData.name, mFormData.password)

            val retrofit = Retrofit.Builder()
                .baseUrl(getString(R.string.backend_url))
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            mService = retrofit.create(PositionsWebService::class.java)
        }

        override fun doInBackground(vararg voids: Void): String? {
            val c = mService.register(mRegisterRequest)
            try {
                val response = c.execute()
                if (response.isSuccessful) {
                    return response.body()!!.api_key
                }
                Log.e(TAG, "Unsuccessful response: " + response.errorBody())
            } catch (e: IOException) {
                Log.e(TAG, "IOException: $e")
            }

            return null
        }

        override fun onPostExecute(apiKey: String?) {
            onFinish()

            if (apiKey == null) {
                mEmailView.error = getString(R.string.error_user_already_exist)
                mEmailView.requestFocus()
            } else {
                onSuccessfulRegister(apiKey)
            }
        }

        override fun onCancelled() {
            onFinish()
        }

        private fun onFinish() {
            mRegisterTask = null
            showProgress(false)
        }
    }
}

package `in`.aerem.comconbeacons

import `in`.aerem.comconbeacons.models.ProfileRequest
import `in`.aerem.comconbeacons.models.UserResponse
import `in`.aerem.comconbeacons.models.getBackendUrl
import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.SearchManager
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.animation.OvershootInterpolator
import android.widget.SearchView
import com.github.clans.fab.FloatingActionButton
import com.github.clans.fab.FloatingActionMenu
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class MainActivity : AppCompatActivity() {
    private val TAG = "ComConBeacons"
    private val PERMISSION_REQUEST_COARSE_LOCATION = 1
    private val REQUEST_ENABLE_BT = 2
    private lateinit var mService: PositionsWebService
    private val mHandler = Handler()
    private lateinit var mListUpdateRunnable: Runnable

    private var mFilterString = ""
    private val mLiveData = MutableLiveData<List<UserListItem>>()
    private val mSortedFilteredLiveData = MutableLiveData<List<UserListItem>>()

    private lateinit var mStatusMenu: FloatingActionMenu
    private lateinit var mSecurityToken: String

    enum class SortBy {
        NAME,
        LOCATION,
        STATUS,
        FRESHNESS,
    }

    private var mSortBy: SortBy = SortBy.FRESHNESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verify the action and get the query
        if (Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also { query -> onSearchQuery(query) }
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(getBackendUrl(application,this))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        mService = retrofit.create(PositionsWebService::class.java)

        mSecurityToken = (application as ComConBeaconsApplication).getGlobalSharedPreferences()
            .getString(getString(R.string.token_preference_key), "")!!

        // See RecyclerView guide for details if needed
        // https://developer.android.com/guide/topics/ui/layout/recyclerview
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = UsersPositionsAdapter()
        mLiveData.observe(this, Observer {
                data: List<UserListItem>? ->
            if (data != null) mSortedFilteredLiveData.postValue(filteredResults(sortedResults(data)))
        })
        mSortedFilteredLiveData.observe(this,
            Observer { data: List<UserListItem>? -> if (data != null) adapter.setData(data) })

        // TODO: Consider using better approach when LiveData
        // (https://developer.android.com/topic/libraries/architecture/livedata)
        // is coming from ViewModel class
        // (https://developer.android.com/topic/libraries/architecture/viewmodel)
        // which in turn takes it from Repository. See Android Jetpack architecture guide for details:
        // https://developer.android.com/jetpack/docs/guide
        // Also see concrete example: https://medium.com/@guendouz/room-livedata-and-recyclerview-d8e96fb31dfe
        mListUpdateRunnable = object : Runnable {
            override fun run() {
                mService.users().enqueue(object : Callback<List<UserResponse>> {
                    override fun onResponse(call: Call<List<UserResponse>>, response: Response<List<UserResponse>>) {
                        Log.i(TAG, "Http request succeeded, response = " + response.body())
                        var lines = ArrayList<UserListItem>()
                        for (u in response.body()!!) {
                            lines.add(UserListItem(u))
                        }

                        // Last seven days
                        var recentEntries = lines.filter { item -> Date().time - item.date.time < 1000 * 60 * 60 * 24 * 7 }
                        // More recent entries first
                        mLiveData.postValue(recentEntries)
                    }

                    override fun onFailure(call: Call<List<UserResponse>>, t: Throwable) {
                        Log.e(TAG, "Http request failed: $t")
                    }
                })
                mHandler.postDelayed(this, 10000)
            }
        }
        recyclerView.adapter = adapter

        mStatusMenu = findViewById(R.id.menu_status)

        findViewById<FloatingActionButton>(R.id.menu_status_free).setOnClickListener { setStatus("free") }
        findViewById<FloatingActionButton>(R.id.menu_status_adventure).setOnClickListener { setStatus("adventure") }
        findViewById<FloatingActionButton>(R.id.menu_status_busy).setOnClickListener { setStatus("busy") }

        createCustomAnimation()
    }


    public override fun onPause() {
        super.onPause()
        mHandler.removeCallbacks(mListUpdateRunnable)
    }

    private fun setStatus(s: String) {
        val r = ProfileRequest(s)
        val c = mService.profile(mSecurityToken, r)
        c.enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                mStatusMenu.close(true)
                // Hack to instantly refresh data, as server seems not to be read-after-write consistent
                mLiveData.postValue((mLiveData.value!!.map { u: UserListItem ->
                    if (u.id == response.body()!!.id) {
                        u.setStatusFromString(s)
                    }
                    u
                }))
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                Log.e(TAG, "Http request failed: $t")
            }
        })
     }

    private fun createCustomAnimation() {
        val set = AnimatorSet()

        val scaleOutX = ObjectAnimator.ofFloat(mStatusMenu.menuIconView, "scaleX", 1.0f, 0.2f)
        val scaleOutY = ObjectAnimator.ofFloat(mStatusMenu.menuIconView, "scaleY", 1.0f, 0.2f)

        val scaleInX = ObjectAnimator.ofFloat(mStatusMenu.menuIconView, "scaleX", 0.2f, 1.0f)
        val scaleInY = ObjectAnimator.ofFloat(mStatusMenu.menuIconView, "scaleY", 0.2f, 1.0f)

        scaleOutX.duration = 50
        scaleOutY.duration = 50

        scaleInX.duration = 150
        scaleInY.duration = 150

        scaleInX.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                mStatusMenu.menuIconView.setImageResource(
                    if (mStatusMenu.isOpened) R.drawable.status_white else R.drawable.close)
            }
        })

        set.play(scaleOutX).with(scaleOutY)
        set.play(scaleInX).with(scaleInY).after(scaleOutX)
        set.interpolator = OvershootInterpolator(2.0f)

        mStatusMenu.iconToggleAnimatorSet = set
    }

    private fun onSearchQuery(filter: String) {
        mFilterString = filter.toLowerCase()
        mLiveData.postValue(mLiveData.value)
    }

    private fun filteredResults(lines: List<UserListItem>): List<UserListItem> {
        return lines.filter { it ->
                it.username.toLowerCase().contains(mFilterString) ||
                it.location.toLowerCase().contains(mFilterString)
        }
    }

    private fun sortedResults(lines: List<UserListItem>): List<UserListItem> {
        return when (mSortBy) {
            SortBy.FRESHNESS -> lines.sortedBy { item -> item.date }.reversed()
            SortBy.LOCATION -> lines.sortedBy { item -> item.location }
            SortBy.NAME -> lines.sortedBy { item -> item.username.toLowerCase() }
            SortBy.STATUS -> lines.sortedBy { item -> item.status }
        }
    }

    override fun onResume() {
        super.onResume()
        checkEverythingEnabled()
        this.startService(Intent(this, BeaconsScanner::class.java))
        mListUpdateRunnable.run()
    }

    private fun checkEverythingEnabled() {
        checkBluetoothEnabled()
        checkLocationPermission()
        checkLocationService()
    }

    private fun checkBluetoothEnabled() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    private fun checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSION_REQUEST_COARSE_LOCATION
                )
            }
        }
    }

    private fun checkLocationService() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.enable_location))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    startActivityForResult(Intent (Settings.ACTION_LOCATION_SOURCE_SETTINGS), 0)
                }
                .create()
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.toolbar, menu)
        val searchView = (menu.findItem(R.id.action_search).actionView as SearchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextChange(query: String): Boolean {
                onSearchQuery(query)
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                onSearchQuery(query)
                return true
            }

        })

        return true
    }
    @SuppressLint("ApplySharedPref")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_exit) {
            val preferences = (application as ComConBeaconsApplication).getGlobalSharedPreferences()
            preferences.edit().remove(getString(R.string.token_preference_key)).commit()
            this.stopService(Intent(this, BeaconsScanner::class.java))
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        } else if (id == R.id.action_sort) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.sort_by))
                .setSingleChoiceItems(
                    arrayOf(
                        getString(R.string.sort_by_name),
                        getString(R.string.sort_by_location),
                        getString(R.string.sort_by_status),
                        getString(R.string.sort_by_freshness)),
                    mSortBy.ordinal
                ) { dialog: DialogInterface, which: Int ->
                    mSortBy = SortBy.values()[which]
                    mLiveData.postValue(mLiveData.value)
                    dialog.dismiss()
                }
                .create()
                .show()
        }
        return super.onOptionsItemSelected(item)
    }
}

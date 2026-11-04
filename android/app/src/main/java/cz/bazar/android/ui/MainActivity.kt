package cz.bazar.android.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import cz.bazar.android.R

/** Hlavní obrazovka se spodní navigací (domů / hledat / prodat / oblíbené / více). */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        findViewById<View>(R.id.brandbar).setOnClickListener {
            nav.selectedItemId = R.id.tab_home
        }

        nav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.tab_search -> SearchFragment()
                R.id.tab_sell -> SellFragment()
                R.id.tab_fav -> FavoritesFragment()
                R.id.tab_more -> MoreFragment()
                else -> FeedFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
            true
        }

        // Při rotaci necháme fragment obnovit systém, jinak vložíme feed.
        if (savedInstanceState == null) nav.selectedItemId = R.id.tab_home
    }
}
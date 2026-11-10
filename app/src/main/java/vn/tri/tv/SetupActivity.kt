package vn.tri.tv

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        val url = findViewById<EditText>(R.id.url)
        url.setText(Store.playlistUrl(this))
        val save = {
            val v = url.text.toString().trim()
            if (v.isEmpty()) Toast.makeText(this, "Chưa nhập link", Toast.LENGTH_SHORT).show()
            else {
                Store.setPlaylistUrl(this, v)
                setResult(RESULT_OK)
                finish()
            }
        }
        findViewById<TextView>(R.id.save).setOnClickListener { save() }
        url.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
        url.requestFocus()
    }
}

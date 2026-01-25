package sample.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(
                if(isDark){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dynamicDarkColorScheme(this)
                    } else {
                        darkColorScheme()
                    }
                } else{
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dynamicLightColorScheme(this)
                    } else {
                        lightColorScheme()
                    }
                }
            ) {
                App(isDark)
            }
        }
    }
}
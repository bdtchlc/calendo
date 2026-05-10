package com.calendo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import com.calendo.app.R
import com.calendo.app.ui.CalendoViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(
    vm: CalendoViewModel,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            vm.setGoogleAccount(account.email)
            vm.setSyncHint("已获取账号授权。完整双向日历同步需在 Google Cloud 配置 OAuth 并接入 Calendar API（见 README）。")
        } catch (e: ApiException) {
            vm.setSyncHint("Google 登录失败：${e.statusCode} ${e.message}")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
    ) {
        TopAppBar(
            title = { Text("我的", fontWeight = FontWeight.SemiBold) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "同步与账号",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (state.googleAccountEmail != null) {
                            "当前 Google：${state.googleAccountEmail}"
                        } else {
                            "尚未连接 Google 账号"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.lastSyncHint?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val client = GoogleSignIn.getClient(context, buildGoogleSignInOptions(context))
                    signInLauncher.launch(client.signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("连接 Google 日历（OAuth）")
            }

            OutlinedButton(
                onClick = {
                    GoogleSignIn.getClient(context, buildGoogleSignInOptions(context)).signOut()
                        .addOnCompleteListener {
                            vm.setGoogleAccount(null)
                            vm.setSyncHint("已退出 Google 账号（本地数据保留）。")
                        }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("退出 Google")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "说明：双向同步需要 Calendar API 与后台同步队列；当前版本完成登录与令牌获取框架，数据仍以本地示例为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildGoogleSignInOptions(context: Context): GoogleSignInOptions {
    val webId = context.getString(R.string.default_web_client_id)
    val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
    if (webId.isNotBlank() && webId != "REPLACE_ME") {
        builder.requestIdToken(webId)
    }
    return builder.build()
}

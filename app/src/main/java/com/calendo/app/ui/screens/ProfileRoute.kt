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
import androidx.compose.material3.LinearProgressIndicator
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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
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
            vm.setSyncHint("正在与 Google 日历同步…")
            vm.syncWithGoogleCalendar()
        } catch (e: ApiException) {
            vm.setSyncHint(googleSignInErrorMessage(context, e))
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
                    if (state.googleSyncInProgress) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        )
                    }
                }
            }

            val playOk = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

            Button(
                onClick = {
                    if (!playOk) {
                        vm.setSyncHint("当前设备无法使用 Google Play 服务，无法登录 Google 日历。")
                        return@Button
                    }
                    val webId = context.getString(R.string.default_web_client_id)
                    if (webId.isBlank() || webId == "REPLACE_ME") {
                        vm.setSyncHint(
                            "请先在 res/values/strings.xml 将 default_web_client_id 换成 Google Cloud「OAuth 2.0 客户端」里的 Web 应用客户端 ID，并在同一项目中添加 Android 客户端（包名 $PACKAGE_NAME + 调试 SHA-1）。详见 README。",
                        )
                        return@Button
                    }
                    val client = GoogleSignIn.getClient(context, buildGoogleSignInOptions(context))
                    signInLauncher.launch(client.signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.googleSyncInProgress,
            ) {
                Text("连接 Google 日历")
            }

            Button(
                onClick = {
                    if (state.googleAccountEmail == null) {
                        vm.setSyncHint("请先连接 Google 账号。")
                        return@Button
                    }
                    vm.syncWithGoogleCalendar()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.googleSyncInProgress && state.googleAccountEmail != null,
            ) {
                Text("立即同步 Google 日历")
            }

            OutlinedButton(
                onClick = {
                    GoogleSignIn.getClient(context, buildGoogleSignInOptions(context)).signOut()
                        .addOnCompleteListener {
                            vm.setGoogleAccount(null)
                            vm.setSyncHint("已退出 Google（本地日程仍保留在本机内存中）。")
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.googleSyncInProgress,
            ) {
                Text("退出 Google")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "连接后将申请日历读写权限；保存、删除或与网页端日历变更会通过同步合并（双向）。首次使用必须在 Cloud Console 启用 Calendar API，并正确配置 OAuth。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PACKAGE_NAME = "com.calendo.app"

private fun googleSignInErrorMessage(context: Context, e: ApiException): String {
    val detail = e.message?.let { "（$it）" }.orEmpty()
    return when (e.statusCode) {
        10 -> "开发者配置错误（DEVELOPER_ERROR）：请在 Google Cloud 核对 Android OAuth（包名 $PACKAGE_NAME + 调试 SHA-1），并把 Web 客户端 ID 填入 default_web_client_id。$detail"
        12500 -> "登录失败：内部错误。$detail"
        12501 -> "已取消登录。"
        12502 -> "网络不可用，请检查网络后重试。$detail"
        7 -> "网络错误。$detail"
        8 -> "内部错误，请稍后重试。$detail"
        else -> "Google 登录失败（错误码 ${e.statusCode}）。$detail"
    }
}

private fun buildGoogleSignInOptions(context: Context): GoogleSignInOptions {
    val webId = context.getString(R.string.default_web_client_id)
    val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
            Scope("https://www.googleapis.com/auth/calendar"),
            Scope("https://www.googleapis.com/auth/tasks"),
        )
    if (webId.isNotBlank() && webId != "REPLACE_ME") {
        builder.requestIdToken(webId)
    }
    return builder.build()
}

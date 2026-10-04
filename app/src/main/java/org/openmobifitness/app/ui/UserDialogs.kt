@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.openmobifitness.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import org.openmobifitness.app.*
import org.openmobifitness.app.R
import org.openmobifitness.app.data.*
import org.openmobifitness.core.*

internal fun profileLabel(user: UserProfile,users: List<UserProfile>): String = user.name + if(users.count { it.normalizedName==user.normalizedName }>1) " · ${user.id.take(8)}" else ""

@Composable internal fun UserAvatar(c: Controller,name: String,reference: String="",size: Dp=40.dp) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,reference) {
        value=if(reference.isEmpty()) null else withContext(Dispatchers.IO) { runCatching { android.graphics.BitmapFactory.decodeFile(c.repo.avatars.file(reference).absolutePath)?.asImageBitmap() }.getOrNull() }
    }
    Surface(Modifier.size(size),shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment=Alignment.Center) {
            if(bitmap!=null) Image(bitmap!!,null,Modifier.fillMaxSize().clip(CircleShape))
            else {
                val chars=name.trim().codePoints().toArray(); val index=if(chars.size==2 && chars.first()== '小'.code) 1 else 0
                val initial=chars.getOrNull(index)?.let { String(Character.toChars(it)) } ?: "·"
                Text(initial,style=if(size>=64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
@Composable internal fun UserChip(c: Controller,onClick: ()->Unit) {
    val user by c.currentUser.collectAsStateWithLifecycle(); val state by c.state.collectAsStateWithLifecycle(); val maintenance by c.repo.maintenance.collectAsStateWithLifecycle()
    Surface(onClick=onClick,enabled=!c.identityLocked,shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.heightIn(min=48.dp).widthIn(max=160.dp).testTag("current-user")) {
        Row(Modifier.padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            UserAvatar(c,user?.name.orEmpty(),user?.avatar.orEmpty(),30.dp)
            Text(state.session?.startedUserName ?: user?.name ?: stringResource(R.string.user_select),Modifier.weight(1f,false),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge)
            Icon(if(state.session!=null || maintenance) Icons.Default.Lock else Icons.Default.ArrowDropDown,null,Modifier.size(18.dp))
        }
    }
}

@Composable internal fun UserPanel(title: String,onClose: ()->Unit,footer: @Composable ()->Unit,content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),contentAlignment=Alignment.Center) {
            val compact=maxHeight<420.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>=1.6f
            Surface(Modifier.widthIn(max=640.dp).fillMaxWidth().heightIn(max=760.dp),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.background) {
                val heading: @Composable ()->Unit = { Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    IconButton(onClick=onClose) { Icon(Icons.Default.Close,stringResource(R.string.close)) }
                } }
                if(compact) Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) { heading(); content(); footer() }
                else Column(Modifier.padding(20.dp)) {
                    heading()
                    Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
                    footer()
                }
            }
        }
    }
}

@Composable internal fun UserPicker(c: Controller,request: StartRequest?,onClose: ()->Unit,onManage: ()->Unit) {
    val users by c.repo.users.collectAsStateWithLifecycle(); val current by c.currentUser.collectAsStateWithLifecycle()
    var selected by rememberSaveable(request?.id) { mutableStateOf(current?.id.orEmpty()) }
    var creating by rememberSaveable { mutableStateOf(false) }
    if(creating) {
        UserEditor(c,null,request?.id,{ creating=false }) { created -> selected=created.id; creating=false }
        return
    }
    UserPanel(stringResource(R.string.user_select),onClose,footer={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={ if(request!=null) c.confirmStart(request.id,selected) else { c.switchUser(selected); onClose() } },enabled=users.any { it.id==selected && it.available },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("confirm-user")) { Text(stringResource(if(request==null) R.string.user_use else R.string.user_confirm_start)) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={ creating=true },modifier=Modifier.testTag("add-user")) { Icon(Icons.Default.Add,null); Text(stringResource(R.string.user_add)) }
                if(request==null) TextButton(onClick=onManage) { Text(stringResource(R.string.user_manage)) }
                else TextButton(onClick=onClose) { Text(stringResource(R.string.cancel)) }
            }
        }
    }) {
        if(request!=null) Text(stringResource(R.string.user_start_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(users.none { it.available }) Text(stringResource(R.string.user_empty),style=MaterialTheme.typography.bodyLarge)
        users.filter { it.available }.forEach { user ->
            Surface(onClick={ selected=user.id },shape=RoundedCornerShape(18.dp),color=if(selected==user.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,if(selected==user.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().testTag("user-${user.id}")) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    UserAvatar(c,user.name,user.avatar)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(profileLabel(user,users),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        Text(if(user.id==current?.id) stringResource(R.string.user_current) else if(user.weightKg==null) stringResource(R.string.user_weight_unknown) else "${org.openmobifitness.app.service.WorkoutService.number(user.weightKg)} kg",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected==user.id,null)
                }
            }
        }
    }
}

@Composable internal fun UserEditor(c: Controller,original: UserProfile?,requestId: String?=null,onClose: ()->Unit,onSaved: (UserProfile)->Unit) {
    val activity=androidx.activity.compose.LocalActivity.current as? MainActivity
    val draftId=rememberSaveable { original?.id ?: java.util.UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var avatar by rememberSaveable { mutableStateOf(original?.avatar.orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(original?.weightKg?.let { org.openmobifitness.app.service.WorkoutService.number(it) }.orEmpty()) }
    var met by rememberSaveable { mutableStateOf((original?.met ?: 5.0).let { org.openmobifitness.app.service.WorkoutService.number(it) }) }
    var metChanged by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(0) }; var advanced by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }; var busy by remember { mutableStateOf(false) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var photoCandidates by rememberSaveable { mutableStateOf(arrayListOf(original?.avatar.orEmpty())) }
    val scope=rememberCoroutineScope()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if(uri!=null) scope.launch {
        busy=true
        try { photo=withContext(Dispatchers.IO) { c.repo.avatars.select(uri) }; photoCandidates=ArrayList((photoCandidates+photo!!).distinct()) } catch(_: Exception) { error=R.string.user_photo_failed } finally { busy=false }
    } }
    var discard by remember { mutableStateOf(false) }
    fun closeEditor() { c.discardAvatars(photoCandidates.toSet()); onClose() }
    fun close() { if(!busy) {
        val changed=name!=original?.name.orEmpty() || avatar!=original?.avatar.orEmpty() || weight!=(original?.weightKg?.let { org.openmobifitness.app.service.WorkoutService.number(it) }.orEmpty()) || metChanged
        if(changed) discard=true else closeEditor()
    } }
    androidx.activity.compose.BackHandler { close() }
    if(discard) DiscardDialog({ discard=false },::closeEditor)
    fun validName()=name.trim().let { it.isNotBlank() && it.codePointCount(0,it.length)<=24 && it.none(Char::isISOControl) }
    UserPanel(stringResource(if(original==null) R.string.user_add else R.string.user_edit),::close,footer={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            error?.let { Text(stringResource(it),color=MaterialTheme.colorScheme.error) }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={ if(step==1 && original==null) step=0 else close() },enabled=!busy) { Text(stringResource(if(step==1 && original==null) R.string.back else R.string.cancel)) }
                Button(enabled=!busy,onClick={
                    error=null
                    if(!validName()) { error=R.string.user_invalid_name; return@Button }
                    if(original==null && step==0) { step=1; return@Button }
                    scope.launch {
                        busy=true
                        try {
                            val value=UserProfile(id=draftId,name=name.trim(),avatar=avatar,weightKg=weight.trim().takeIf { it.isNotEmpty() }?.replace(',','.')?.toDouble(),met=met.replace(',','.').toDouble(),metSource=if(metChanged) "user" else original?.metSource ?: "default",createdAt=original?.createdAt ?: System.currentTimeMillis(),legacyHints=original?.legacyHints.orEmpty())
                            c.saveProfile(value,requestId)
                            c.discardAvatars(photoCandidates.toSet())
                            onSaved(value)
                        } catch(e: Exception) { error=when { e.message=="duplicate_user_name" -> R.string.user_duplicate; e.message=="identity_locked" -> R.string.identity_locked; e.message=="avatar_missing" -> R.string.user_photo_failed; e is IllegalArgumentException -> R.string.user_invalid_body; else -> R.string.storage_failed } }
                        finally { busy=false }
                    }
                },modifier=Modifier.testTag("save-user")) { Text(stringResource(if(original==null && step==0) R.string.user_next else R.string.save)) }
            }
        }
    }) {
        if(original==null) Text("${step+1} / 2  ·  "+stringResource(if(step==0) R.string.user_basic else R.string.user_energy),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        if(original!=null || step==0) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                UserAvatar(c,name,avatar,72.dp)
                Column {
                    TextButton(onClick={ activity?.openImagePicker { picker.launch("image/*") } ?: picker.launch("image/*") },enabled=!busy,modifier=Modifier.testTag("choose-avatar")) { Text(stringResource(R.string.user_photo)) }
                    if(avatar.isNotEmpty()) TextButton(onClick={ avatar="" },enabled=!busy) { Text(stringResource(R.string.user_photo_clear)) }
                }
            }
            OutlinedTextField(name,{ name=it },label={ Text(stringResource(R.string.user_name)) },singleLine=true,modifier=Modifier.fillMaxWidth().testTag("user-name"),enabled=!busy)
            Text(stringResource(R.string.user_name_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(original!=null || step==1) {
            if(original==null) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { UserAvatar(c,name,avatar,56.dp); Text(name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
            OutlinedTextField(weight,{ weight=it },label={ Text(stringResource(R.string.user_weight_optional)) },placeholder={ Text(stringResource(R.string.user_weight_unknown)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("user-weight"),enabled=!busy)
            Text(stringResource(R.string.user_estimates_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={ advanced=!advanced }) { Text(stringResource(R.string.user_met)); Icon(if(advanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,null) }
            if(advanced) OutlinedTextField(met,{ met=it; metChanged=true },label={ Text(stringResource(R.string.user_met)) },singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),enabled=!busy)
            Text(stringResource(R.string.user_met_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    photo?.let { reference ->
        fun cancelPhoto() { photo=null; if(reference!=avatar) c.discardAvatars(setOf(reference)) }
        AlertDialog(onDismissRequest=::cancelPhoto,title={ Text(stringResource(R.string.user_photo_preview)) },text={ Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { UserAvatar(c,name,reference,160.dp) } },confirmButton={ TextButton(onClick={ avatar=reference; photo=null }) { Text(stringResource(R.string.user_photo_apply)) } },dismissButton={ TextButton(onClick=::cancelPhoto) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable internal fun UserManagement(c: Controller) {
    val users by c.repo.users.collectAsStateWithLifecycle(); val current by c.currentUser.collectAsStateWithLifecycle()
    val state by c.state.collectAsStateWithLifecycle(); val maintenance by c.repo.maintenance.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }; var removed by rememberSaveable { mutableStateOf(false) }
    var removal by remember { mutableStateOf<UserRemovalPreview?>(null) }; val scope=rememberCoroutineScope()
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(state.session!=null || maintenance) Text(stringResource(R.string.identity_locked),color=MaterialTheme.colorScheme.onSurfaceVariant)
        users.filter { it.available }.forEach { user ->
            OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        UserAvatar(c,user.name,user.avatar,48.dp)
                        Column(Modifier.weight(1f)) { Text(profileLabel(user,users),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); if(current?.id==user.id) Text(stringResource(R.string.user_current),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) }
                        IconButton(onClick={ editing=user.id },enabled=c.dataActionsAllowed) { Icon(Icons.Default.Edit,stringResource(R.string.user_edit)) }
                    }
                    TextButton(onClick={ scope.launch { removal=c.repo.removalPreview(user.id) } },enabled=c.dataActionsAllowed) { Text(stringResource(R.string.user_remove)) }
                }
            }
        }
        LegacyClaim(c)
        OutlinedButton(onClick={ editing="new" },enabled=c.dataActionsAllowed,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(Icons.Default.Add,null); Text(stringResource(R.string.user_add)) }
        TextButton(onClick={ removed=!removed }) { Text(stringResource(R.string.user_removed)+" · "+users.count { !it.available && !it.deleted }); Icon(if(removed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,null) }
        if(removed) users.filter { !it.available && !it.deleted }.forEach { user ->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                UserAvatar(c,user.name,user.avatar); Text(profileLabel(user,users),Modifier.weight(1f))
                TextButton(enabled=c.dataActionsAllowed,onClick={ scope.launch { runCatching { c.idleOperation { c.repo.restoreUser(user.id) } }.onFailure { c.error(if(it.message=="duplicate_user_name") R.string.user_duplicate else R.string.storage_failed) } } }) { Text(stringResource(R.string.user_restore)) }
            }
        }
    }
    editing?.let { id -> UserEditor(c,users.firstOrNull { it.id==id },onClose={ editing=null },onSaved={ editing=null; c.reloadPreferences() }) }
    removal?.let { preview ->
        var delete by remember(preview) { mutableStateOf(false) }; var checked by remember(preview) { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
        UserPanel(stringResource(R.string.user_remove),{ if(!busy) removal=null },footer={
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(enabled=!busy,onClick={ removal=null }) { Text(stringResource(R.string.cancel)) }
                Button(enabled=!busy && (!delete || checked),onClick={ scope.launch { busy=true; runCatching { c.idleOperation { c.repo.removeUser(preview,delete,checked) } }.onSuccess { removal=null; if(c.currentUser.value==null) c.userPicker.value=true }.onFailure { c.error(R.string.storage_failed); removal=null }; busy=false } },colors=if(delete) ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),modifier=Modifier.testTag("confirm-remove-user")) { Text(stringResource(R.string.user_remove)) }
            }
        }) {
            Text(users.firstOrNull { it.id==preview.userId }?.name.orEmpty(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(stringResource(R.string.user_remove_summary,preview.sessions,preview.workouts),style=MaterialTheme.typography.bodyMedium)
            listOf(false to R.string.user_remove_keep,true to R.string.user_remove_delete).forEach { (value,label) ->
                Row(Modifier.fillMaxWidth().clickable { delete=value; checked=false }.heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) { RadioButton(delete==value,null); Text(stringResource(label),Modifier.weight(1f)) }
            }
            if(delete) {
                Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp)) { Text(stringResource(R.string.user_delete_warning),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer) }
                Row(Modifier.fillMaxWidth().clickable { checked=!checked },verticalAlignment=Alignment.CenterVertically) { Checkbox(checked,{ checked=it },modifier=Modifier.testTag("confirm-delete-data")); Text(stringResource(R.string.user_delete_confirm),Modifier.weight(1f)) }
            }
        }
    }
}

@Composable internal fun historyScopeName(c: Controller,scope: String,user: UserProfile?,users: List<UserProfile>): String=when(scope) {
    "current" -> user?.name ?: stringResource(R.string.user_select)
    "all" -> stringResource(R.string.history_all_users)
    "unassigned" -> stringResource(R.string.history_unassigned)
    "removed" -> stringResource(R.string.user_removed)
    else -> users.firstOrNull { it.id==scope }?.let { profileLabel(it,users) } ?: stringResource(R.string.user_removed)
}
@Composable internal fun UserScopePicker(c: Controller,initial: String,onSelect: (String)->Unit,onClose: ()->Unit) {
    val users by c.repo.users.collectAsStateWithLifecycle(); val current by c.currentUser.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(initial) }
    UserPanel(stringResource(R.string.history_scope),onClose,footer={ Button(onClick={ onSelect(selected) },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.confirm)) } }) {
        val choices=listOf("current" to (stringResource(R.string.user_current)+" · "+(current?.name ?: "—")),"all" to stringResource(R.string.history_all_users),"unassigned" to stringResource(R.string.history_unassigned),"removed" to stringResource(R.string.user_removed)) + users.filter { !it.deleted }.map { it.id to (profileLabel(it,users)+if(!it.available) " · "+stringResource(R.string.user_removed) else "") }
        choices.forEach { (id,label) -> Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { selected=id },verticalAlignment=Alignment.CenterVertically) {
            RadioButton(selected==id,null); Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        } }
    }
}

@Composable private fun LegacyClaim(c: Controller) {
    val revision by c.repo.revision.collectAsStateWithLifecycle(); val users by c.repo.users.collectAsStateWithLifecycle()
    var count by remember { mutableIntStateOf(0) }; var ids by remember { mutableStateOf<List<String>?>(null) }
    var selected by rememberSaveable { mutableStateOf("") }; var confirm by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(revision) { count=c.repo.history.page(HistoryQuery(owner="unassigned"),limit=1).count }
    if(count>0) OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_unassigned),fontWeight=FontWeight.Bold)
            Text(stringResource(R.string.user_claim_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled=c.dataActionsAllowed && !busy && users.any { it.available },onClick={ busy=true; scope.launch { runCatching { c.repo.history.reportIds(HistoryQuery(owner="unassigned")) }.onSuccess { ids=it; selected=c.currentUser.value?.id.orEmpty() }.onFailure { c.error(R.string.storage_failed) }; busy=false } }) { Text(stringResource(R.string.user_claim_count,count)) }
        }
    }
    ids?.let { records -> UserPanel(stringResource(R.string.user_claim),{ if(!busy) { ids=null; confirm=false } },footer={
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            TextButton(enabled=!busy,onClick={ ids=null; confirm=false }) { Text(stringResource(R.string.cancel)) }
            Button(enabled=!busy && users.any { it.id==selected && it.available },onClick={
                if(!confirm) confirm=true else { busy=true; scope.launch { runCatching { c.idleOperation { c.repo.changeOwners(records,selected,records.associateWith { null }) } }.onSuccess { ids=null; confirm=false }.onFailure { ids=null; confirm=false; c.error(R.string.storage_failed) }; busy=false } }
            }) { Text(stringResource(if(confirm) R.string.confirm else R.string.user_next)) }
        }
    }) {
        if(confirm) { Text(stringResource(R.string.user_claim_preview,records.size,users.firstOrNull { it.id==selected }?.name.orEmpty()),style=MaterialTheme.typography.titleMedium); Text(stringResource(R.string.report_owner_help),style=MaterialTheme.typography.bodyMedium) }
        else users.filter { it.available }.forEach { user -> Row(Modifier.fillMaxWidth().clickable { selected=user.id }.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) { RadioButton(selected==user.id,null); UserAvatar(c,user.name,user.avatar,32.dp); Text(profileLabel(user,users),Modifier.weight(1f).padding(start=12.dp)) } }
    } }
}

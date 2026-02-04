package com.example.todolist
// 保持你的 package 名字不变
// package com.example.simpletodo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
// 引入太阳和月亮图标
import androidx.compose.material.icons.filled.Brightness4 // 月亮
import androidx.compose.material.icons.filled.Brightness7 // 太阳
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

// --- 数据结构 ---
data class TodoItem(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    var isDone: Boolean = false,
    val colorArgb: Int,
    val startTime: String = "",
    val endTime: String = ""
)

// 定义颜色列表 (标签颜色保持亮色，比较好看)
val todoColors = listOf(
    Color(0xFFFFFFFF), Color(0xFFFFF176), Color(0xFFFF8A80),
    Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFE1BEE7)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 获取上下文
            val context = LocalContext.current
            // 1. 读取保存的主题状态 (默认 false = 日间模式)
            var isDarkTheme by remember { mutableStateOf(loadThemeMode(context)) }

            // 2. 定义两套颜色方案
            val lightScheme = lightColorScheme(
                background = Color(0xFFF5F5F5), // 浅灰背景
                surface = Color.White,
                onBackground = Color.Black,
                primary = Color(0xFF6200EE)
            )
            val darkScheme = darkColorScheme(
                background = Color(0xFF121212), // 纯黑/深灰背景
                surface = Color(0xFF1E1E1E),
                onBackground = Color.White,
                primary = Color(0xFFBB86FC)
            )

            // 3. 将整个 App 包裹在 MaterialTheme 中，并根据 isDarkTheme 切换颜色
            MaterialTheme(
                colorScheme = if (isDarkTheme) darkScheme else lightScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background // 使用主题背景色
                ) {
                    TodoApp(
                        isDark = isDarkTheme,
                        onThemeToggle = {
                            // 切换模式并保存
                            val newState = !isDarkTheme
                            isDarkTheme = newState
                            saveThemeMode(context, newState)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(
    isDark: Boolean,
    onThemeToggle: () -> Unit
) {
    val context = LocalContext.current
    val todoList = remember { mutableStateListOf<TodoItem>() }
    var showDialog by remember { mutableStateOf(false) }
    var currentEditingItem by remember { mutableStateOf<TodoItem?>(null) }

    LaunchedEffect(Unit) {
        val savedList = loadData(context)
        todoList.addAll(savedList)
    }

    fun saveAll() {
        saveData(context, todoList)
    }

    Scaffold(
        // --- 核心变化：增加了顶部栏 ---
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "简待办",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background // 顶部栏颜色跟随背景
                ),
                navigationIcon = {
                    // 左上角的切换按钮
                    IconButton(onClick = onThemeToggle) {
                        Icon(
                            // 根据模式切换图标：月亮 <-> 太阳
                            imageVector = if (isDark) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                            contentDescription = "切换主题",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    currentEditingItem = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // 这里很重要，防止内容被顶部栏遮挡
                .padding(horizontal = 16.dp)
        ) {
            // 列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(todoList, key = { it.id }) { item ->
                    AdvancedTodoItemRow(
                        item = item,
                        onCheckedChange = { isChecked ->
                            val index = todoList.indexOf(item)
                            if (index != -1) {
                                todoList[index] = item.copy(isDone = isChecked)
                                saveAll()
                            }
                        },
                        onEditClick = {
                            currentEditingItem = item
                            showDialog = true
                        },
                        onDelete = {
                            todoList.remove(item)
                            saveAll()
                        }
                    )
                }
            }
        }
    }

    if (showDialog) {
        TodoDialog(
            initialItem = currentEditingItem,
            onDismiss = { showDialog = false },
            onConfirm = { text, color, start, end ->
                if (text.isNotBlank()) {
                    if (currentEditingItem == null) {
                        todoList.add(0, TodoItem(
                            text = text,
                            colorArgb = color.toArgb(),
                            startTime = start,
                            endTime = end
                        ))
                    } else {
                        val index = todoList.indexOfFirst { it.id == currentEditingItem!!.id }
                        if (index != -1) {
                            todoList[index] = currentEditingItem!!.copy(
                                text = text,
                                colorArgb = color.toArgb(),
                                startTime = start,
                                endTime = end
                            )
                        }
                    }
                    saveAll()
                    showDialog = false
                } else {
                    Toast.makeText(context, "请输入内容", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// --- 组件：通用弹窗 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDialog(
    initialItem: TodoItem?,
    onDismiss: () -> Unit,
    onConfirm: (String, Color, String, String) -> Unit
) {
    val context = LocalContext.current

    fun getCurrentTimeStr(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            "%d-%02d-%02d %02d:%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    var text by remember { mutableStateOf(initialItem?.text ?: "") }
    var selectedColor by remember { mutableStateOf(if(initialItem != null) Color(initialItem.colorArgb) else todoColors[0]) }

    var startTime by remember {
        mutableStateOf(
            if (initialItem == null) getCurrentTimeStr()
            else (initialItem.startTime.ifEmpty { "未设置" })
        )
    }
    var endTime by remember { mutableStateOf(initialItem?.endTime?.ifEmpty { "未设置" } ?: "未设置") }


    fun showDateTimePicker(onResult: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                val formattedTime = String.format(
                    "%d-%02d-%02d %02d:%02d",
                    year, month + 1, day, hour, minute
                )
                onResult(formattedTime)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "新建待办" else "修改待办") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("标签颜色:", fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    todoColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == color) 2.dp else 0.dp,
                                    color = Color.Black, // 选中圈圈的边框始终是黑色，看起比较明显
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("时间设置 (年-月-日 时:分):", fontSize = 14.sp)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showDateTimePicker { startTime = it } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("开始: $startTime", color = Color.Black, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { showDateTimePicker { endTime = it } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("结束: $endTime", color = Color.Black, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text, selectedColor, startTime, endTime) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// --- 组件：列表项 ---
@Composable
fun AdvancedTodoItemRow(
    item: TodoItem,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = Color(item.colorArgb)
    // 逻辑微调：如果已完成，卡片变灰；如果未完成，显示用户选的颜色
    val displayColor = if (item.isDone) Color(0xFFE0E0E0) else cardColor

    // 文字颜色：因为我们的卡片颜色都是浅色(粉色、浅蓝等)，所以文字用黑色最清晰。
    // 即使在黑夜模式下，卡片本身也是亮的，所以字依然保持黑色或深灰色。
    val textColor = if (item.isDone) Color.Gray else Color.Black

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = displayColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.isDone,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.Gray,
                        checkmarkColor = Color.White,
                        uncheckedColor = Color.DarkGray
                    )
                )

                Text(
                    text = item.text,
                    fontSize = 18.sp,
                    textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                    color = textColor,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color.DarkGray)
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray)
                }
            }

            if (item.startTime != "未设置" || item.endTime != "未设置") {
                Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.Black.copy(alpha = 0.1f))
                Row(modifier = Modifier.padding(start = 12.dp)) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        if (item.startTime != "未设置") Text("始: ${item.startTime}", fontSize = 11.sp, color = textColor)
                        if (item.endTime != "未设置") Text("终: ${item.endTime}", fontSize = 11.sp, color = textColor)
                    }
                }
            }
        }
    }
}

// --- 数据存储工具函数 (新增了主题存储) ---

// 1. 保存/读取 主题模式
fun saveThemeMode(context: Context, isDark: Boolean) {
    val sharedPreferences = context.getSharedPreferences("TodoData", Context.MODE_PRIVATE)
    sharedPreferences.edit().putBoolean("isDarkTheme", isDark).apply()
}

fun loadThemeMode(context: Context): Boolean {
    val sharedPreferences = context.getSharedPreferences("TodoData", Context.MODE_PRIVATE)
    return sharedPreferences.getBoolean("isDarkTheme", false) // 默认是 false (日间)
}

// 2. 保存/读取 待办列表 (不变)
fun saveData(context: Context, list: List<TodoItem>) {
    val sharedPreferences = context.getSharedPreferences("TodoData", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    val gson = Gson()
    val json = gson.toJson(list)
    editor.putString("taskList", json)
    editor.apply()
}

fun loadData(context: Context): List<TodoItem> {
    val sharedPreferences = context.getSharedPreferences("TodoData", Context.MODE_PRIVATE)
    val json = sharedPreferences.getString("taskList", null)
    return if (json != null) {
        val gson = Gson()
        val type = object : TypeToken<List<TodoItem>>() {}.type
        gson.fromJson(json, type)
    } else {
        emptyList()
    }
}
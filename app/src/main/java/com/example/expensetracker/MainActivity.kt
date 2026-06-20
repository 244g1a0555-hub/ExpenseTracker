package com.example.expensetracker

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Firebase initialization failed", e)
        }
        setContent {
            ExpenseHomeScreen()
        }
    }
}

data class ExpenseItem(
    val amount: Int,
    val dateTime: String,
    val type: String,
    val category: String = "",
    val docId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseHomeScreen() {
    @Composable
    fun PieChart(
        food: Int,
        travel: Int,
        shopping: Int,
        education: Int
    ) {
        val total = food + travel + shopping + education
        Canvas(
            modifier = Modifier.size(300.dp)
        ) {
            if (total == 0) {
                drawArc(
                    color = Color.LightGray,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
            } else {
                val foodAngle = (food.toFloat() / total) * 360f
                val travelAngle = (travel.toFloat() / total) * 360f
                val shoppingAngle = (shopping.toFloat() / total) * 360f
                val educationAngle = (education.toFloat() / total) * 360f

                var currentAngle = 0f
                drawArc(
                    color = Color.Red,
                    startAngle = currentAngle,
                    sweepAngle = foodAngle,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
                currentAngle += foodAngle

                drawArc(
                    color = Color.Blue,
                    startAngle = currentAngle,
                    sweepAngle = travelAngle,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
                currentAngle += travelAngle

                drawArc(
                    color = Color.Green,
                    startAngle = currentAngle,
                    sweepAngle = shoppingAngle,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
                currentAngle += shoppingAngle

                drawArc(
                    color = Color.Yellow,
                    startAngle = currentAngle,
                    sweepAngle = educationAngle,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
            }
        }
    }
    val context = LocalContext.current
    val db = remember {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("ExpenseHomeScreen", "Firestore not available", e)
            null
        }
    }

    val sharedPreferences = remember {
        context.getSharedPreferences("expense_prefs", Context.MODE_PRIVATE)
    }
    val gson = remember { Gson() }

    var income by remember { mutableIntStateOf(0) }
    var expense by remember { mutableIntStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var isIncomeSelected by remember { mutableStateOf(false) }

    val categories = listOf("Food", "Travel", "Shopping", "Education", "Other")
    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val expenseList = remember { mutableStateListOf<ExpenseItem>() }
    var showDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableIntStateOf(-1) }
    var darkMode by remember { mutableStateOf(false) }

    // Function to save data locally
    fun saveLocally(list: List<ExpenseItem>) {
        val json = gson.toJson(list)
        sharedPreferences.edit().putString("expense_list", json).apply()
    }

    // Function to load data locally
    fun loadLocally() {
        val json = sharedPreferences.getString("expense_list", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<ExpenseItem>>() {}.type
                val savedList: List<ExpenseItem> = gson.fromJson(json, type)
                expenseList.clear()
                expenseList.addAll(savedList)
                
                // Recalculate totals
                var tempIncome = 0
                var tempExpense = 0
                savedList.forEach {
                    if (it.type == "Income") tempIncome += it.amount else tempExpense += it.amount
                }
                income = tempIncome
                expense = tempExpense
            } catch (e: Exception) {
                Log.e("ExpenseHomeScreen", "Error loading local data", e)
            }
        }
    }

    val balance = income - expense
    val scrollState = rememberScrollState()

    // Initial Load
    LaunchedEffect(Unit) {
        loadLocally() // Load local data first for speed

        db?.collection("expenses")
            ?.get()
            ?.addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    expenseList.clear()
                    var tempIncome = 0
                    var tempExpense = 0
                    for (doc in result) {
                        val amt = (doc.getLong("amount") ?: 0L).toInt()
                        val type = doc.getString("type") ?: "Expense"
                        val category = doc.getString("category") ?: ""
                        val dateTime = doc.getString("dateTime") ?: ""
                        expenseList.add(ExpenseItem(amt, dateTime, type, category, doc.id))
                        if (type == "Income") tempIncome += amt else tempExpense += amt
                    }
                    income = tempIncome
                    expense = tempExpense
                    saveLocally(expenseList) // Sync local cache with Firestore
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (darkMode) Color.DarkGray else Color.White)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Logo
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp),
            contentScale = ContentScale.Fit
        )

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Income: ₹$income", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                Text("Expense: ₹$expense", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Balance: ₹$balance", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dark Mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dark Mode", color = if (darkMode) Color.White else Color.Black)
            Switch(
                checked = darkMode,
                onCheckedChange = { darkMode = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Income / Expense toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { isIncomeSelected = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isIncomeSelected) Color(0xFF2E7D32) else Color.LightGray
                )
            ) {
                Text("Income")
            }
            Button(
                onClick = { isIncomeSelected = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isIncomeSelected) Color(0xFFC62828) else Color.LightGray
                )
            ) {
                Text("Expense")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Enter Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category dropdown - only for Expense
        if (!isIncomeSelected) {
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selectedCategory = category
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                val value = amountText.toIntOrNull() ?: 0
                if (value > 0) {
                    val currentTime = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                    val type = if (isIncomeSelected) "Income" else "Expense"
                    val category = if (isIncomeSelected) "" else selectedCategory

                    val data = hashMapOf(
                        "amount" to value,
                        "type" to type,
                        "category" to category,
                        "dateTime" to currentTime
                    )

                    if (db != null) {
                        db.collection("expenses").add(data)
                            .addOnSuccessListener { docRef ->
                                if (isIncomeSelected) income += value else expense += value
                                expenseList.add(ExpenseItem(value, currentTime, type, category, docRef.id))
                                saveLocally(expenseList)
                            }
                    } else {
                        // Offline fallback
                        if (isIncomeSelected) income += value else expense += value
                        expenseList.add(ExpenseItem(value, currentTime, type, category, "local_" + UUID.randomUUID().toString()))
                        saveLocally(expenseList)
                    }
                    amountText = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isIncomeSelected) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        ) {
            Text(if (isIncomeSelected) "Add Income" else "Add Expense")
        }

        val foodTotal = expenseList
            .filter { it.category == "Food" }
            .sumOf { it.amount }

        val travelTotal = expenseList
            .filter { it.category == "Travel" }
            .sumOf { it.amount }

        val shoppingTotal = expenseList
            .filter { it.category == "Shopping" }
            .sumOf { it.amount }

        val educationTotal = expenseList
            .filter { it.category == "Education" }
            .sumOf { it.amount }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Statistics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))
        PieChart(foodTotal, travelTotal, shoppingTotal, educationTotal)
        Text("🔴 Food = ₹$foodTotal")
        Text("🔵 Travel = ₹$travelTotal")
        Text("🟢 Shopping = ₹$shoppingTotal")
        Text("🟡 Education = ₹$educationTotal")

        Spacer(modifier = Modifier.height(16.dp))
        Text("Expense History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))

        expenseList.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "₹${item.amount} (${item.type})",
                        color = if (item.type == "Income") Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                    if (item.category.isNotEmpty()) {
                        Text(item.category, fontSize = 12.sp, color = Color.DarkGray)
                    }
                    Text(item.dateTime, fontSize = 12.sp, color = Color.Gray)
                }
                IconButton(onClick = {
                    itemToDelete = index
                    showDialog = true
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
            }
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    itemToDelete = -1
                },
                title = { Text("Delete Entry") },
                text = { Text("Are you sure you want to delete this entry?") },
                confirmButton = {
                    TextButton(onClick = {
                        if (itemToDelete != -1) {
                            val index = itemToDelete
                            val item = expenseList[index]
                            if (item.type == "Income") income -= item.amount else expense -= item.amount
                            
                            if (db != null && item.docId.isNotEmpty() && !item.docId.startsWith("local_")) {
                                db.collection("expenses").document(item.docId).delete()
                            }
                            expenseList.removeAt(index)
                            saveLocally(expenseList)
                        }
                        showDialog = false
                        itemToDelete = -1
                    }) {
                        Text("Yes", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDialog = false
                        itemToDelete = -1
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

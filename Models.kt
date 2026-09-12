package com.rutta.app.data

import androidx.room.*

@Entity(tableName="orders") data class Order(@PrimaryKey(autoGenerate=true) val id:Long=0,val address:String,val reference:String,val phone:String,val customer:String,val amount:Long,val date:String,val time:String,val status:String="Pendiente")
@Entity(tableName="expenses") data class Expense(@PrimaryKey(autoGenerate=true) val id:Long=0,val category:String,val amount:Long,val description:String,val date:String,val time:String="",val note:String="")
@Dao interface OrderDao { @Query("SELECT * FROM orders ORDER BY id DESC") fun all():kotlinx.coroutines.flow.Flow<List<Order>>; @Insert suspend fun insert(o:Order) }
@Dao interface ExpenseDao { @Query("SELECT * FROM expenses ORDER BY id DESC") fun all():kotlinx.coroutines.flow.Flow<List<Expense>>; @Insert suspend fun insert(e:Expense) }
@Database(entities=[Order::class,Expense::class],version=1,exportSchema=false) abstract class RuttaDb:RoomDatabase(){ abstract fun orders():OrderDao; abstract fun expenses():ExpenseDao }

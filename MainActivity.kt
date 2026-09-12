package com.rutta.app

import android.content.*
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import androidx.room.Room
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rutta.app.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:ComponentActivity(){
 private val db by lazy{Room.databaseBuilder(applicationContext,RuttaDb::class.java,"rutta.db").build()}
 private val picker=registerForActivityResult(ActivityResultContracts.GetContent()){ uri-> if(uri!=null) ocr(uri) }
 private var ocrText by mutableStateOf("")
 override fun onCreate(b:Bundle?){super.onCreate(b); setContent{RuttaApp(db,{picker.launch("image/*")},{openCamera()})}}
 private fun openCamera(){ startActivity(Intent(this,CameraActivity::class.java)) }
 private fun ocr(uri:Uri){ TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(this,uri)).addOnSuccessListener{ocrText=it.text; startActivity(Intent(this,ReviewActivity::class.java).putExtra("ocr",ocrText))} }
}

@Composable fun RuttaApp(db:RuttaDb,pick:()->Unit,camera:()->Unit){
 val nav=rememberNavController(); var dark by remember{mutableStateOf(false)}
 MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()){
  Scaffold(bottomBar={NavigationBar{listOf("Comanda" to Icons.Default.Home,"Pedidos" to Icons.Default.Inventory,"Beneficios" to Icons.Default.BarChart).forEach{(name,icon)->NavigationBarItem(selected=false,onClick={nav.navigate(name)},icon={Icon(icon,null)},label={Text(name)})}}}){p->NavHost(nav,"Comanda",Modifier.padding(p)){
   composable("Comanda"){Home(pick,camera)}; composable("Pedidos"){Orders(db)}; composable("Beneficios"){Benefits(db)}; composable("Gastos"){Expenses(db)}; composable("Calendario"){CalendarScreen(db)}; composable("Cuenta"){Account()}; composable("Configuracion"){Settings(dark,{dark=it})}
  }}
}
@Composable fun Home(pick:()->Unit,camera:()->Unit){Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Text("RUTTA",style=MaterialTheme.typography.headlineLarge);Text("Tu jornada, bajo control",style=MaterialTheme.typography.titleMedium);Button(camera,Modifier.fillMaxWidth().height(80.dp),shape=RoundedCornerShape(20.dp)){Icon(Icons.Default.CameraAlt,null);Spacer(Modifier.width(8.dp));Text("ESCANEAR COMANDA")};OutlinedButton(pick,Modifier.fillMaxWidth().height(60.dp)){Icon(Icons.Default.Photo,null);Spacer(Modifier.width(8.dp));Text("ELEGIR FOTO")};OutlinedButton({},Modifier.fillMaxWidth().height(60.dp)){Text("✍️ INGRESAR MANUALMENTE")};Spacer(Modifier.height(8.dp));Text("Accesos",style=MaterialTheme.typography.titleLarge);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SmallCard("💸","Gastos");SmallCard("📅","Calendario");SmallCard("⚙️","Configuración")}}}
@Composable fun SmallCard(a:String,b:String)=Card(Modifier.weight(1f)){Column(Modifier.padding(14.dp)){Text(a);Text(b)}}
@Composable fun Orders(db:RuttaDb){val list by db.orders().all().collectAsState(emptyList());Column(Modifier.padding(16.dp)){Text("📦 Pedidos",style=MaterialTheme.typography.headlineMedium);Text("Hoy: ${list.count()}");LazyColumn{items(list){o->Card(Modifier.fillMaxWidth().padding(vertical=6.dp)){Column(Modifier.padding(16.dp)){Text(o.customer.ifBlank{"Cliente"},style=MaterialTheme.typography.titleMedium);Text("📍 ${o.address}");Text("💰 $${o.amount}");Row{TextButton({dial(LocalContext.current,o.phone)}){Text("📞 Llamar")};TextButton({maps(LocalContext.current,o.address)}){Text("📍 GPS")};TextButton({whatsapp(LocalContext.current,o.phone)}){Text("💬 WhatsApp")}}}}}}}}
@Composable fun Benefits(db:RuttaDb){val os by db.orders().all().collectAsState(emptyList());val es by db.expenses().all().collectAsState(emptyList());val income=os.sumOf{it.amount};val exp=es.sumOf{it.amount};Column(Modifier.padding(16.dp)){Text("📊 BENEFICIOS",style=MaterialTheme.typography.headlineMedium);Row{FilterChip(true,{},label={Text("DÍA")});Spacer(Modifier.width(8.dp));FilterChip(false,{},label={Text("SEMANA")});Spacer(Modifier.width(8.dp));FilterChip(false,{},label={Text("MES")})};Metric("💰 Ingresos","$${income}");Metric("📦 Pedidos","${os.size}");Metric("⛽ Combustible","$${es.filter{it.category=="Combustible"}.sumOf{it.amount}}");Metric("🔧 Otros gastos","$${exp-es.filter{it.category=="Combustible"}.sumOf{it.amount}}");Metric("💵 Ganancia neta","$${income-exp}")}}
@Composable fun Metric(a:String,b:String)=Card(Modifier.fillMaxWidth().padding(vertical=6.dp)){Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(a);Text(b,style=MaterialTheme.typography.titleLarge)}}
@Composable fun Expenses(db:RuttaDb){val scope=rememberCoroutineScope();var amount by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var cat by remember{mutableStateOf("Combustible")};Column(Modifier.padding(16.dp)){Text("💸 GASTOS",style=MaterialTheme.typography.headlineMedium);TextField(cat,{cat=it},Modifier.fillMaxWidth(),label={Text("Categoría")});TextField(amount,{amount=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Monto")});TextField(desc,{desc=it},Modifier.fillMaxWidth(),label={Text("Descripción")});Button({scope.launch{db.expenses().insert(Expense(category=cat,amount=amount.toLongOrNull()?:0,description=desc,date=today()))};amount="";desc=""},Modifier.fillMaxWidth()){Text("GUARDAR GASTO")};Spacer(Modifier.height(12.dp));val es by db.expenses().all().collectAsState(emptyList());LazyColumn{items(es){e->Text("${e.date} · ${e.category} · $${e.amount}",Modifier.padding(8.dp))}}}}
@Composable fun CalendarScreen(db:RuttaDb){val os by db.orders().all().collectAsState(emptyList());val es by db.expenses().all().collectAsState(emptyList());val days=(os.map{it.date}+es.map{it.date}).distinct().sortedDescending();Column(Modifier.padding(16.dp)){Text("📅 CALENDARIO",style=MaterialTheme.typography.headlineMedium);days.forEach{d->val i=os.filter{it.date==d}.sumOf{it.amount};val e=es.filter{it.date==d}.sumOf{it.amount};Card(Modifier.fillMaxWidth().padding(5.dp)){Column(Modifier.padding(14.dp)){Text(d);Text("Pedidos: ${os.count{it.date==d}}  Ingresos: $$i  Gastos: $$e  Neto: $${i-e}")}}}}}
@Composable fun Account(){Column(Modifier.padding(20.dp)){Text("👤 CUENTA",style=MaterialTheme.typography.headlineMedium);Text("Modo local activo");Text("La autenticación online (Google/teléfono) requiere backend y credenciales propias; no se simula.")}}
@Composable fun Settings(dark:Boolean,setDark:(Boolean)->Unit){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("⚙️ CONFIGURACIÓN",style=MaterialTheme.typography.headlineMedium);Row{Text("🌙 Modo oscuro");Spacer(Modifier.weight(1f));Switch(dark,setDark)};Text("🔔 Notificaciones");Text("🔊 Sonidos");Text("📤 Exportar datos");Text("🔐 Privacidad");Text("ℹ️ Acerca de RUTTA");Text("🚪 Cerrar sesión")}}
fun today()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
fun dial(c:Context,p:String){c.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+p)))}
fun maps(c:Context,a:String){c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(a))))}
fun whatsapp(c:Context,p:String){val n=if(p.startsWith("+"))p.filter{it.isDigit()} else "56"+p.filter{it.isDigit()}.removePrefix("56");try{c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$n")))}catch(_:Exception){}}

class CameraActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);
  val preview=PreviewView(this); val capture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
  val root=android.widget.FrameLayout(this);root.addView(preview);val btn=android.widget.Button(this);btn.text="CAPTURAR COMANDA";val lp=android.widget.FrameLayout.LayoutParams(-1,160);lp.gravity=android.view.Gravity.BOTTOM;root.addView(btn,lp);setContentView(root);
  val f=ProcessCameraProvider.getInstance(this);f.addListener({val c=f.get();val prev=Preview.Builder().build();prev.setSurfaceProvider(preview.surfaceProvider);c.unbindAll();c.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,prev,capture);btn.setOnClickListener{val file=java.io.File(cacheDir,"rutta_${System.currentTimeMillis()}.jpg");val out=ImageCapture.OutputFileOptions.Builder(file).build();capture.takePicture(out,ContextCompat.getMainExecutor(this),object:ImageCapture.OnImageSavedCallback{override fun onImageSaved(r:ImageCapture.OutputFileResults){TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(this@CameraActivity,Uri.fromFile(file))).addOnSuccessListener{startActivity(Intent(this@CameraActivity,ReviewActivity::class.java).putExtra("ocr",it.text))}};override fun onError(e:ImageCaptureException){}})}} ,ContextCompat.getMainExecutor(this))
 }
}
class ReviewActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);val text=intent.getStringExtra("ocr")?:"";setContent{Column(Modifier.padding(20.dp)){Text("Revisar comanda",style=MaterialTheme.typography.headlineMedium);Text("Texto detectado",style=MaterialTheme.typography.titleMedium);Text(text.ifBlank{"No se detectó texto. Puedes ingresar los datos manualmente."});Button({finish()},Modifier.fillMaxWidth()){Text("VOLVER")}}}}}

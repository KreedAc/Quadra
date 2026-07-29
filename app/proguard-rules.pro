# Regole per la build di release, dove R8 rimuove e rinomina.
#
# Quasi tutto quello che c'è qui protegge una cosa sola: i nomi. Il database e il file
# di backup contengono nomi scritti — di classi, di campi, di costanti — e R8 non sa che
# quei nomi vengono riletti da fuori il programma. Se li accorcia, l'app continua a
# compilare, continua a partire, e sbaglia soltanto quando qualcuno prova a ripristinare
# un backup. È il tipo di guasto che non si vede provando l'app, perché in debug R8 non
# gira affatto.

# Room genera codice che riflette sui nomi delle entità.
-keep class it.quadra.data.db.** { *; }

# kotlinx.serialization: i serializzatori sono generati a compilazione e raggiunti
# attraverso il companion, quindi R8 non vede nessuna chiamata diretta e li tratta come
# codice morto. Sono le regole pubblicate dalla libreria stessa.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Le costanti degli enum del dominio finiscono scritte per nome sia nelle colonne del
# database sia nel JSON del backup. Rinominarle significa che un archivio scritto da una
# versione non è più leggibile da quella dopo — e i dati sono di chi li ha inseriti.
-keepclassmembers enum it.quadra.core.model.** { *; }

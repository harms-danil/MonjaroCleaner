# Shizuku instantiates the user service by class name inside a separate app_process,
# trying the (Context) constructor first, then the no-arg one.
-keep class app.mcleaner.privileged.ShellUserService { <init>(...); *; }
-keep class app.mcleaner.IShellService { *; }
-keep class app.mcleaner.IShellService$* { *; }

# Shizuku API / provider
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.server.** { *; }
-dontwarn rikka.**

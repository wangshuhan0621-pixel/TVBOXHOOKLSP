#!/system/bin/sh
# TVBox 直接 Hook 脚本
# 使用 app_process 运行，无需 LSPosed

LOG_DIR="/sdcard/TVBoxHook"
PID=$(pidof com.ysc.tvbox)

echo "================================"
echo "TVBox Direct Hook"
echo "================================"
echo ""

if [ -z "$PID" ]; then
    echo "[错误] TVBox 应用未运行"
    echo "请先启动 TVBox 应用"
    exit 1
fi

echo "[+] TVBox PID: $PID"
echo "[+] 创建日志目录..."
mkdir -p $LOG_DIR

echo ""
echo "[+] 开始监控..."
echo "[*] 监控文件: /sdcard/TVBoxHook/hook.log"
echo ""

# 使用 strace 监控
su -c "strace -p $PID -e trace=openat,read,write -o $LOG_DIR/strace.log" &

# 监控内存映射
su -c "cat /proc/$PID/maps > $LOG_DIR/maps.txt"

echo "[+] 监控已启动"
echo "[*] 请在 TVBox 应用中操作以触发解密"
echo "[*] 按 Ctrl+C 停止监控"
echo ""

# 等待
wait

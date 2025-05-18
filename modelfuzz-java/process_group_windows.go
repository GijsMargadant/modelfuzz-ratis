//go:build windows
// +build windows

package main

import (
	"os/exec"
	// "syscall"
)

// SetupProcessGroup does nothing on Windows
func SetupProcessGroup(cmd *exec.Cmd) {
	// cmd.SysProcAttr = &syscall.SysProcAttr{
	// 	CreationFlags: syscall.CREATE_NEW_PROCESS_GROUP,
	// }
}

// KillProcessGroup kills the main process on Windows
func KillProcessGroup(pid int) error {
	// dll := syscall.MustLoadDLL("kernel32.dll")
	// proc := dll.MustFindProc("GenerateConsoleCtrlEvent")

	// const CTRL_BREAK_EVENT = 1

	// r1, _, err := proc.Call(uintptr(CTRL_BREAK_EVENT), uintptr(pid))
	// if r1 == 0 {
	// 	return err
	// }
	return nil
}

//go:build !windows
// +build !windows

package main

import (
	"os/exec"
	"syscall"
)

// SetupProcessGroup sets the process group on Unix systems
func SetupProcessGroup(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setpgid: true,
	}
}

// KillProcessGroup kills the process group on Unix systems
func KillProcessGroup(pid int) error {
	return syscall.Kill(-pid, syscall.SIGKILL)
}

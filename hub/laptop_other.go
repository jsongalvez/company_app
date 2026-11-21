//go:build !windows

package main

import "errors"

var errWindowsOnly = errors.New("the laptop program runs on Windows only")

func laptopMain() int { panic(errWindowsOnly) }

func setupElevated() error { return errWindowsOnly }

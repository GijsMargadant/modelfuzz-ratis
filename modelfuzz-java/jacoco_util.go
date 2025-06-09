package main

import (
	"encoding/xml"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

type ReportCounter struct {
	Missed  int `xml:"missed,attr"`
	Covered int `xml:"covered,attr"`
}

type ReportCoverage struct {
	XMLName xml.Name `xml:"report"`
	Counter []struct {
		Type    string `xml:"type,attr"`
		Missed  int    `xml:"missed,attr"`
		Covered int    `xml:"covered,attr"`
	} `xml:"counter"`
}

func ComputeCoverageIncrease(newCoverageReport string, oldCoverageReport string) (int, error) {
	newCoverage, err := lineCoverage(newCoverageReport)
	if err != nil {
		return 0, fmt.Errorf("error parsing new report: %w", err)
	}

	oldCoverage, err := lineCoverage(oldCoverageReport)
	if err != nil {
		return 0, fmt.Errorf("error parsing old report: %w", err)
	}

	increase := newCoverage - oldCoverage
	if increase < 0 {
		increase = 0
	}

	return increase, nil
}

func lineCoverage(reportPath string) (int, error) {
	data, err := os.ReadFile(reportPath)
	if err != nil {
		return 0, err
	}

	var report ReportCoverage
	if err := xml.Unmarshal(data, &report); err != nil {
		return 0, err
	}

	for _, counter := range report.Counter {
		if counter.Type == "LINE" {
			return counter.Covered, nil
		}
	}

	return 0, fmt.Errorf("LINE coverage not found in report: %s", reportPath)
}

func generateJacocoReport(jacocoCli string, jar string, execFile string, outputFile string) error {
	// _, _, sourceDirs, err := DiscoverMavenProject(jar)
	// if err != nil {
	// 	return err
	// }

	args := []string{
		"-jar", jacocoCli,
		"report", execFile,
	}

	args = append(args, "--classfiles", jar)
	// for _, sourceDir := range sourceDirs {
	// 	args = append(args, "--sourcefiles", sourceDir)
	// }

	if strings.Contains(outputFile, ".xml") {
		args = append(args, "--xml", outputFile)
	} else if strings.Contains(outputFile, ".html") {
		args = append(args, "--html", outputFile)
	} else {
		panic("Unexpected file type extension: " + outputFile)
	}
	cmd := exec.Command("java", args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func GetJacocoFiles(baseDir string) ([]string, error) {
	entries, err := os.ReadDir(baseDir)
	if err != nil {
		return nil, err
	}

	nodeFolders := []string{}
	for _, entry := range entries {
		if entry.IsDir() {
			nodeFolders = append(nodeFolders, entry.Name())
		}
	}

	jacocoFiles := []string{}
	for _, folder := range nodeFolders {
		p := filepath.Join(baseDir, folder, "jacoco.exec")

		if _, err := os.Stat(p); err == nil {
			jacocoFiles = append(jacocoFiles, p)
		} else if os.IsNotExist(err) {
			continue
		} else {
			return nil, err
		}
	}

	return jacocoFiles, nil
}

func MergeCoverageFiles(jacocoCli string, coverageFiles []string, mergedOutput string) error {
	args := []string{"-jar", jacocoCli, "merge"}
	args = append(args, coverageFiles...)
	args = append(args, "--destfile", mergedOutput)

	cmd := exec.Command("java", args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	return cmd.Run()
}

// DiscoverMavenProject intelligently finds the root and gathers class/source dirs
func DiscoverMavenProject(jarPath string) (projectRoot string, classDirs []string, sourceDirs []string, err error) {
	absJarPath, err := filepath.Abs(jarPath)
	if err != nil {
		return "", nil, nil, err
	}

	// Step 1: Walk upward to find the *aggregator* pom.xml (with <modules>)
	dir := filepath.Dir(absJarPath)
	for {
		pomPath := filepath.Join(dir, "pom.xml")
		if _, statErr := os.Stat(pomPath); statErr == nil {
			isAggregator, parseErr := isAggregatorPom(pomPath)
			if parseErr != nil {
				return "", nil, nil, parseErr
			}
			if isAggregator {
				projectRoot = dir
				break
			}
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", nil, nil, fmt.Errorf("aggregator pom.xml not found starting from %s", jarPath)
		}
		dir = parent
	}

	// Step 2: Walk from root and gather class/source dirs
	err = filepath.WalkDir(projectRoot, func(path string, d os.DirEntry, err error) error {
		if err != nil || !d.IsDir() {
			return nil
		}
		if filepath.Base(path) == "classes" && filepath.Base(filepath.Dir(path)) == "target" {
			classDirs = append(classDirs, path)
		}
		if filepath.Base(path) == "java" && strings.HasSuffix(path, "/src/main/java") {
			sourceDirs = append(sourceDirs, path)
		}
		return nil
	})
	if err != nil {
		return "", nil, nil, err
	}

	return projectRoot, classDirs, sourceDirs, nil
}

type MavenProject struct {
	Modules []string `xml:"modules>module"`
}

// isAggregatorPom returns true if the pom.xml contains <modules>
func isAggregatorPom(pomPath string) (bool, error) {
	data, err := os.ReadFile(pomPath)
	if err != nil {
		return false, err
	}
	var proj MavenProject
	if err := xml.Unmarshal(data, &proj); err != nil {
		return false, err
	}
	return len(proj.Modules) > 0, nil
}

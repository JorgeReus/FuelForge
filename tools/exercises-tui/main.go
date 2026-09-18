package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/charmbracelet/bubbles/list"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

const dataURL = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/data/exercises.json"

type Exercise struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Category  string `json:"category"`
	Equipment string `json:"equipment"`
	Target    string `json:"target"`
	GIF       string `json:"gif_url"`
}
type model struct {
	exercises, filtered []Exercise
	list                list.Model
	query, status       string
}
type gifReady struct {
	path string
	name string
	meta string
}

type exerciseItem Exercise

func (e exerciseItem) FilterValue() string {
	return e.Name + " " + e.Category + " " + e.Equipment + " " + e.Target
}
func (e exerciseItem) Title() string       { return e.Name }
func (e exerciseItem) Description() string { return e.Equipment + " · " + e.Target }

func main() {
	exercises, err := loadExercises()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	items := make([]list.Item, len(exercises))
	for i, exercise := range exercises {
		items[i] = exerciseItem(exercise)
	}
	delegate := list.NewDefaultDelegate()
	exerciseList := list.New(items, delegate, 80, 24)
	exerciseList.Title = "Nutri Exercises"
	exerciseList.SetFilteringEnabled(false)
	_, err = tea.NewProgram(model{exercises: exercises, filtered: exercises, list: exerciseList, status: fmt.Sprintf("%d exercises · type to search · enter opens GIF · q quits", len(exercises))}, tea.WithAltScreen()).Run()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func loadExercises() ([]Exercise, error) {
	cache := filepath.Join(must(os.UserCacheDir()), "nutri", "exercises.json")
	if data, err := os.ReadFile(cache); err == nil {
		var result []Exercise
		return result, json.Unmarshal(data, &result)
	}
	response, err := http.Get(dataURL)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("download dataset: %s", response.Status)
	}
	var result []Exercise
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		return nil, err
	}
	if err := os.MkdirAll(filepath.Dir(cache), 0o755); err == nil {
		if data, e := json.Marshal(result); e == nil {
			_ = os.WriteFile(cache, data, 0o644)
		}
	}
	return result, nil
}
func must(value string, err error) string {
	if err != nil {
		panic(err)
	}
	return value
}
func (m model) Init() tea.Cmd { return nil }

func (m model) Update(message tea.Msg) (tea.Model, tea.Cmd) {
	if gif, ok := message.(gifReady); ok {
		return m, showGIF(gif)
	}
	key, ok := message.(tea.KeyMsg)
	if !ok {
		return m, nil
	}
	switch key.String() {
	case "q", "ctrl+c":
		return m, tea.Quit
	case "up", "k":
		var cmd tea.Cmd
		m.list, cmd = m.list.Update(message)
		return m, cmd
	case "down", "j":
		var cmd tea.Cmd
		m.list, cmd = m.list.Update(message)
		return m, cmd
	case "backspace":
		if len(m.query) > 0 {
			m.query = m.query[:len(m.query)-1]
			m.applyFilter()
		}
	case "enter":
		if item, ok := m.list.SelectedItem().(exerciseItem); ok {
			return m, downloadGIF(Exercise(item))
		}
	default:
		if len(key.Runes) > 0 && key.Runes[0] >= 32 {
			m.query += string(key.Runes)
			m.applyFilter()
		}
	}
	var cmd tea.Cmd
	m.list, cmd = m.list.Update(message)
	return m, cmd
}

func (m *model) applyFilter() {
	query := strings.ToLower(m.query)
	m.filtered = m.filtered[:0]
	for _, exercise := range m.exercises {
		if query == "calisthenics" || query == "bodyweight" {
			if strings.EqualFold(exercise.Equipment, "body weight") {
				m.filtered = append(m.filtered, exercise)
			}
			continue
		}
		text := strings.ToLower(exercise.Name + " " + exercise.Category + " " + exercise.Equipment + " " + exercise.Target)
		if strings.Contains(text, query) {
			m.filtered = append(m.filtered, exercise)
		}
	}
	items := make([]list.Item, len(m.filtered))
	for i, exercise := range m.filtered {
		items[i] = exerciseItem(exercise)
	}
	cmd := m.list.SetItems(items)
	m.status = fmt.Sprintf("%d matches · calisthenics = body weight · enter opens GIF · q quits", len(m.filtered))
	_ = cmd
}

func (m model) View() string {
	return lipgloss.JoinVertical(lipgloss.Left, "Search: "+m.query+"_", m.list.View(), m.status)
}

func downloadGIF(exercise Exercise) tea.Cmd {
	return func() tea.Msg {
		path := filepath.Join(must(os.UserCacheDir()), "nutri", "exercises", exercise.ID+".gif")
		if _, err := os.Stat(path); os.IsNotExist(err) {
			if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
				return nil
			}
			response, err := http.Get("https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/" + exercise.GIF)
			if err != nil {
				return nil
			}
			defer response.Body.Close()
			file, err := os.Create(path)
			if err != nil {
				return nil
			}
			_, _ = file.ReadFrom(response.Body)
			_ = file.Close()
		}
		return gifReady{path: path, name: exercise.Name, meta: exercise.Category + " · " + exercise.Equipment + " · target: " + exercise.Target}
	}
}

func showGIF(gif gifReady) tea.Cmd {
	title := lipgloss.NewStyle().Foreground(lipgloss.Color("#4BE277")).Bold(true).Render(gif.name)
	meta := lipgloss.NewStyle().Foreground(lipgloss.Color("#FFB95F")).Render(gif.meta)
	prompt := lipgloss.NewStyle().Foreground(lipgloss.Color("#50DFA4")).Render("Press Enter to return")
	modalText := strings.Join([]string{title, meta, prompt}, "\n")
	command := exec.Command("sh", "-c", `printf '\033[2J\033[H%s\n\n' "$1"; kitty +kitten icat --loop=-1 "$2"; read -r _; kitty +kitten icat --clear`, "nutri", modalText, gif.path)
	if runtime.GOOS != "darwin" && os.Getenv("TERM") != "xterm-kitty" && os.Getenv("KITTY_WINDOW_ID") == "" {
		command = exec.Command("xdg-open", gif.path)
	}
	return tea.ExecProcess(command, func(error) tea.Msg { return nil })
}

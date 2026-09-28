package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
)

const sourceURL = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/data/exercises.json"

type exercise struct {
	ID               string          `json:"id"`
	Name             string          `json:"name"`
	Category         string          `json:"category"`
	BodyPart         string          `json:"body_part"`
	Equipment        string          `json:"equipment"`
	Target           string          `json:"target"`
	MuscleGroup      string          `json:"muscle_group"`
	SecondaryMuscles json.RawMessage `json:"secondary_muscles"`
	Instructions     json.RawMessage `json:"instructions"`
	InstructionSteps json.RawMessage `json:"instruction_steps"`
	Image            string          `json:"image"`
	GIF              string          `json:"gif_url"`
	Attribution      string          `json:"attribution"`
}

type row struct {
	ID               string          `json:"id"`
	Name             string          `json:"name"`
	Category         string          `json:"category"`
	BodyPart         string          `json:"body_part"`
	Equipment        string          `json:"equipment"`
	Target           string          `json:"target"`
	MuscleGroup      string          `json:"muscle_group"`
	SecondaryMuscles json.RawMessage `json:"secondary_muscles"`
	Instructions     json.RawMessage `json:"instructions"`
	InstructionSteps json.RawMessage `json:"instruction_steps"`
	ImageURL         string          `json:"image_url"`
	GIFURL           string          `json:"gif_url"`
	Attribution      string          `json:"attribution"`
}

func toRow(e exercise) row {
	if e.Attribution == "" {
		e.Attribution = "© Gym visual — https://gymvisual.com/"
	}
	return row{e.ID, e.Name, e.Category, e.BodyPart, e.Equipment, e.Target, e.MuscleGroup,
		e.SecondaryMuscles, e.Instructions, e.InstructionSteps, e.Image, e.GIF, e.Attribution}
}

func load(path string) ([]row, error) {
	b, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		resp, getErr := http.Get(sourceURL)
		if getErr != nil {
			return nil, getErr
		}
		defer resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			return nil, fmt.Errorf("download: %s", resp.Status)
		}
		b, err = io.ReadAll(resp.Body)
		if err == nil {
			if err = os.MkdirAll(filepath.Dir(path), 0755); err == nil {
				err = os.WriteFile(path, b, 0644)
			}
		}
	}
	if err != nil {
		return nil, err
	}
	var exercises []exercise
	if err := json.Unmarshal(b, &exercises); err != nil {
		return nil, err
	}
	rows := make([]row, len(exercises))
	for i, e := range exercises {
		rows[i] = toRow(e)
	}
	return rows, nil
}

func push(rows []row, url, key string) error {
	for start := 0; start < len(rows); start += 100 {
		end := start + 100
		if end > len(rows) {
			end = len(rows)
		}
		body, err := json.Marshal(rows[start:end])
		if err != nil {
			return err
		}
		req, err := http.NewRequest(http.MethodPost, url+"/rest/v1/exercises", bytes.NewReader(body))
		if err != nil {
			return err
		}
		req.Header.Set("apikey", key)
		req.Header.Set("Authorization", "Bearer "+key)
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Prefer", "resolution=merge-duplicates,return=minimal")
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			return err
		}
		resp.Body.Close()
		if resp.StatusCode >= 300 {
			return fmt.Errorf("batch %d: %s", start/100+1, resp.Status)
		}
	}
	return nil
}

func main() {
	path := flag.String("path", "scripts/data/exercises.json", "dataset JSON path")
	dryRun := flag.Bool("dry-run", false, "transform without contacting Supabase")
	flag.Parse()
	rows, err := load(*path)
	if err != nil {
		panic(err)
	}
	if *dryRun {
		b, _ := json.MarshalIndent(map[string]any{"count": len(rows), "first": rows[0]}, "", "  ")
		fmt.Println(string(b))
		return
	}
	url, key := os.Getenv("SUPABASE_URL"), os.Getenv("SUPABASE_SERVICE_ROLE_KEY")
	if url == "" || key == "" {
		panic("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required")
	}
	if err := push(rows, url, key); err != nil {
		panic(err)
	}
	fmt.Printf("Imported %d exercises\n", len(rows))
}

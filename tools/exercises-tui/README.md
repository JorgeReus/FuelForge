# Exercise TUI

Downloads and caches the exercise dataset, then plays a selected exercise GIF.
Inside Kitty it uses Kitty graphics; elsewhere it opens the system viewer.

```bash
cd tools/exercises-tui
go mod tidy
go run .
```

Controls: type to search, `j`/`k` or arrows to move, `Enter` to open the GIF,
`q` to quit.

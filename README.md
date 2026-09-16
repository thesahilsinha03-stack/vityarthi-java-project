# Library Management System (Java, Single-File CLI)

A command-line Library Management System built in core Java — no external
dependencies, no frameworks, no database server. Everything lives in a
single file, `LibraryManagementSystem.java`, and data is persisted to
plain CSV files so nothing is lost between runs.

## Features

- **Book catalog** — add, remove, search (by ISBN/title/author), and list books
- **Membership** — register members and list them
- **Issue / return workflow** — borrow books with a 14-day loan period
- **Automatic fines** — $5/day charged for late returns
- **Overdue tracking** — list all currently overdue loans
- **Persistence** — all data is saved to `data/*.csv` after every action,
  so nothing is lost when you close the program
- **Pure command line** — no GUI, no database server required

## Project Structure

```
library-management/
├── LibraryManagementSystem.java   # entire application (models, logic, CLI)
├── data/                          # CSV data files (created automatically)
└── README.md
```

Internally, the single file is organized into clearly separated classes
(nested as static inner classes) so the code stays easy to follow despite
being in one file:

| Class | Responsibility |
|---|---|
| `Book` | Book model: ISBN, title, author, copy counts |
| `Member` | Member model: ID, name, email, outstanding fine |
| `Transaction` | A single borrow/return record with issue/due/return dates |
| `FileManager` | Reads/writes CSV files in `data/` |
| `Library` | Core business logic: catalog, membership, issue/return, fines |
| `LibraryException` | Custom checked exception for business-rule errors |
| `LibraryManagementSystem` (outer class) | CLI entry point and menu loop (`main`) |

## Prerequisites

- **Java Development Kit (JDK) 8 or later**, available on your PATH.
  - Check with: `java -version` and `javac -version`
  - If you don't have a JDK, install one:
    - **Windows/macOS/Linux**: [Adoptium Temurin](https://adoptium.net/) (free, recommended)
    - **macOS (Homebrew)**: `brew install openjdk`
    - **Ubuntu/Debian**: `sudo apt install openjdk-21-jdk`

No other dependencies are required — the project only uses the Java
standard library (`java.util`, `java.io`, `java.nio`, `java.time`).

## Setup & Running the Project

### 1. Get the code

```bash
git clone https://github.com/{github-username}/{repo-name}.git
cd {repo-name}
```

(Or simply download `LibraryManagementSystem.java` into a folder.)

### 2. Compile

```bash
javac LibraryManagementSystem.java
```

This produces several `.class` files (one per inner class) in the same
folder.

### 3. Run

```bash
java LibraryManagementSystem
```

You should see:

```
=========================================
 Welcome to the Library Management System
=========================================

--- MENU ---
 1. Add a book
 2. Remove a book
 3. Search books
 4. List all books
 5. Register a member
 6. List all members
 7. Issue a book
 8. Return a book
 9. Pay a member's fine
10. List all transactions
11. List overdue books
 0. Save and exit
Choose an option:
```

Follow the on-screen prompts. Data is automatically written to a `data/`
folder (created next to the `.class` files) as `books.csv`,
`members.csv`, and `transactions.csv`, and reloaded automatically the
next time you start the program.

### 4. (Optional) Run from an IDE

1. Open the project folder in IntelliJ IDEA, Eclipse, or VS Code (with
   the Java Extension Pack).
2. Run `LibraryManagementSystem.java` directly — it contains `main`.

## Example Usage Walkthrough

```
Choose an option: 1
ISBN: 9780134685991
Title: Effective Java
Author: Joshua Bloch
Number of copies: 3
Book added/updated successfully.

Choose an option: 5
Member ID: M001
Name: Alice Johnson
Email: alice@example.com
Member registered successfully.

Choose an option: 7
ISBN of book to issue: 9780134685991
Member ID: M001
Book issued. Transaction ID: T0001 | Due date: 2026-09-29

Choose an option: 8
Transaction ID: T0001
Book returned on 2026-09-15.
```

## Data Storage Format

Data is stored as plain CSV in the `data/` folder:

- `books.csv` — `isbn,title,author,totalCopies,availableCopies`
- `members.csv` — `memberId,name,email,outstandingFine`
- `transactions.csv` — `transactionId,isbn,memberId,issueDate,dueDate,returnDate`

These files are created automatically on first run if they don't exist.
Delete the `data/` folder at any time to reset the system to a clean
state.

## Design Notes

- **Single file, multiple classes**: all classes are nested as `static`
  inner classes inside `LibraryManagementSystem` purely for the
  convenience of a one-file submission. Each still has a single,
  focused responsibility (model / persistence / business logic / CLI),
  matching what you'd find in a multi-file layout.
- **Error handling**: business-rule violations (borrowing an unavailable
  book, duplicate member IDs, removing a book still checked out, etc.)
  throw a custom `LibraryException`, which the CLI loop catches and
  reports without crashing.
- **No external libraries**: builds and runs anywhere a JDK is
  installed — no Maven/Gradle, no internet access, no database
  installation required.

## Troubleshooting

| Problem | Solution |
|---|---|
| `javac: command not found` | Install a JDK (not just a JRE) and ensure it's on your PATH. |
| `Error: Could not find or load main class LibraryManagementSystem` | Make sure you compiled first (`javac LibraryManagementSystem.java`) and are running `java LibraryManagementSystem` from the same folder as the generated `.class` files. |
| Data doesn't persist between runs | Make sure the program has write permission in the folder you're running it from. Data also saves after every action, not just on exit (option `0`). |

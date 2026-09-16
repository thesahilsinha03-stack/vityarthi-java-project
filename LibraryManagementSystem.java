import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Library Management System — single-file edition.
 *
 * Compile: javac LibraryManagementSystem.java
 * Run:     java LibraryManagementSystem
 *
 * All data is persisted as CSV files inside a local "data" folder,
 * which is created automatically on first run.
 */
public class LibraryManagementSystem {

    // ==========================================================
    //  MODEL: Book
    // ==========================================================
    static class Book {
        private String isbn;
        private String title;
        private String author;
        private int totalCopies;
        private int availableCopies;

        Book(String isbn, String title, String author, int totalCopies) {
            this.isbn = isbn;
            this.title = title;
            this.author = author;
            this.totalCopies = totalCopies;
            this.availableCopies = totalCopies;
        }

        String getIsbn() { return isbn; }
        String getTitle() { return title; }
        String getAuthor() { return author; }
        int getTotalCopies() { return totalCopies; }
        int getAvailableCopies() { return availableCopies; }
        void setAvailableCopies(int n) { this.availableCopies = n; }
        void setTotalCopies(int n) { this.totalCopies = n; }
        boolean isAvailable() { return availableCopies > 0; }

        String toCsv() {
            return String.join(",", esc(isbn), esc(title), esc(author),
                    String.valueOf(totalCopies), String.valueOf(availableCopies));
        }

        static Book fromCsv(String line) {
            String[] p = line.split(",", -1);
            Book b = new Book(p[0], p[1], p[2], Integer.parseInt(p[3]));
            b.setAvailableCopies(Integer.parseInt(p[4]));
            return b;
        }

        private static String esc(String v) { return v.replace(",", ";"); }

        @Override
        public String toString() {
            return String.format("%-12s | %-30s | %-20s | %3d/%-3d available",
                    isbn, title, author, availableCopies, totalCopies);
        }
    }

    // ==========================================================
    //  MODEL: Member
    // ==========================================================
    static class Member {
        private String memberId;
        private String name;
        private String email;
        private double outstandingFine;

        Member(String memberId, String name, String email) {
            this.memberId = memberId;
            this.name = name;
            this.email = email;
            this.outstandingFine = 0.0;
        }

        String getMemberId() { return memberId; }
        String getName() { return name; }
        String getEmail() { return email; }
        double getOutstandingFine() { return outstandingFine; }
        void addFine(double amount) { this.outstandingFine += amount; }
        void clearFine() { this.outstandingFine = 0.0; }

        String toCsv() {
            return String.join(",", esc(memberId), esc(name), esc(email),
                    String.valueOf(outstandingFine));
        }

        static Member fromCsv(String line) {
            String[] p = line.split(",", -1);
            Member m = new Member(p[0], p[1], p[2]);
            m.addFine(Double.parseDouble(p[3]));
            return m;
        }

        private static String esc(String v) { return v.replace(",", ";"); }

        @Override
        public String toString() {
            return String.format("%-10s | %-20s | %-25s | Fine: $%.2f",
                    memberId, name, email, outstandingFine);
        }
    }

    // ==========================================================
    //  MODEL: Transaction
    // ==========================================================
    static class Transaction {
        private String transactionId;
        private String isbn;
        private String memberId;
        private LocalDate issueDate;
        private LocalDate dueDate;
        private LocalDate returnDate; // null while book is still out

        Transaction(String transactionId, String isbn, String memberId,
                    LocalDate issueDate, LocalDate dueDate) {
            this.transactionId = transactionId;
            this.isbn = isbn;
            this.memberId = memberId;
            this.issueDate = issueDate;
            this.dueDate = dueDate;
            this.returnDate = null;
        }

        String getTransactionId() { return transactionId; }
        String getIsbn() { return isbn; }
        String getMemberId() { return memberId; }
        LocalDate getIssueDate() { return issueDate; }
        LocalDate getDueDate() { return dueDate; }
        LocalDate getReturnDate() { return returnDate; }
        void setReturnDate(LocalDate d) { this.returnDate = d; }
        boolean isOpen() { return returnDate == null; }

        String toCsv() {
            return String.join(",", transactionId, isbn, memberId,
                    issueDate.toString(), dueDate.toString(),
                    returnDate == null ? "" : returnDate.toString());
        }

        static Transaction fromCsv(String line) {
            String[] p = line.split(",", -1);
            Transaction t = new Transaction(p[0], p[1], p[2],
                    LocalDate.parse(p[3]), LocalDate.parse(p[4]));
            if (p.length > 5 && !p[5].isEmpty()) {
                t.setReturnDate(LocalDate.parse(p[5]));
            }
            return t;
        }

        @Override
        public String toString() {
            String status = isOpen() ? "OUT (due " + dueDate + ")" : "returned " + returnDate;
            return String.format("%-8s | ISBN:%-12s | Member:%-10s | issued %s | %s",
                    transactionId, isbn, memberId, issueDate, status);
        }
    }

    // ==========================================================
    //  Custom checked exception for business-rule violations
    // ==========================================================
    static class LibraryException extends Exception {
        LibraryException(String message) { super(message); }
    }

    // ==========================================================
    //  FileManager — CSV persistence
    // ==========================================================
    static class FileManager {
        private final String booksFile;
        private final String membersFile;
        private final String transactionsFile;

        FileManager(String dataDir) {
            booksFile = dataDir + File.separator + "books.csv";
            membersFile = dataDir + File.separator + "members.csv";
            transactionsFile = dataDir + File.separator + "transactions.csv";
            ensureFilesExist(dataDir);
        }

        private void ensureFilesExist(String dataDir) {
            try {
                Files.createDirectories(Paths.get(dataDir));
                for (String path : new String[]{booksFile, membersFile, transactionsFile}) {
                    File f = new File(path);
                    if (!f.exists()) f.createNewFile();
                }
            } catch (IOException e) {
                System.err.println("Warning: could not initialize data files: " + e.getMessage());
            }
        }

        List<Book> loadBooks() {
            List<Book> list = new ArrayList<>();
            for (String line : readLines(booksFile)) {
                if (!line.isBlank()) list.add(Book.fromCsv(line));
            }
            return list;
        }

        void saveBooks(Collection<Book> books) {
            List<String> lines = new ArrayList<>();
            for (Book b : books) lines.add(b.toCsv());
            writeLines(booksFile, lines);
        }

        List<Member> loadMembers() {
            List<Member> list = new ArrayList<>();
            for (String line : readLines(membersFile)) {
                if (!line.isBlank()) list.add(Member.fromCsv(line));
            }
            return list;
        }

        void saveMembers(Collection<Member> members) {
            List<String> lines = new ArrayList<>();
            for (Member m : members) lines.add(m.toCsv());
            writeLines(membersFile, lines);
        }

        List<Transaction> loadTransactions() {
            List<Transaction> list = new ArrayList<>();
            for (String line : readLines(transactionsFile)) {
                if (!line.isBlank()) list.add(Transaction.fromCsv(line));
            }
            return list;
        }

        void saveTransactions(Collection<Transaction> transactions) {
            List<String> lines = new ArrayList<>();
            for (Transaction t : transactions) lines.add(t.toCsv());
            writeLines(transactionsFile, lines);
        }

        private List<String> readLines(String path) {
            try {
                return Files.readAllLines(Paths.get(path));
            } catch (IOException e) {
                return new ArrayList<>();
            }
        }

        private void writeLines(String path, List<String> lines) {
            try {
                Files.write(Paths.get(path), lines);
            } catch (IOException e) {
                System.err.println("Warning: could not save to " + path + ": " + e.getMessage());
            }
        }
    }

    // ==========================================================
    //  Library — core business logic
    // ==========================================================
    static class Library {
        private static final int LOAN_PERIOD_DAYS = 14;
        private static final double FINE_PER_DAY = 5.0;

        private final Map<String, Book> books = new LinkedHashMap<>();
        private final Map<String, Member> members = new LinkedHashMap<>();
        private final Map<String, Transaction> transactions = new LinkedHashMap<>();

        private final FileManager fileManager;
        private int transactionCounter;

        Library(FileManager fileManager) {
            this.fileManager = fileManager;
            load();
        }

        private void load() {
            for (Book b : fileManager.loadBooks()) books.put(b.getIsbn(), b);
            for (Member m : fileManager.loadMembers()) members.put(m.getMemberId(), m);
            for (Transaction t : fileManager.loadTransactions()) transactions.put(t.getTransactionId(), t);
            transactionCounter = transactions.size();
        }

        void saveAll() {
            fileManager.saveBooks(books.values());
            fileManager.saveMembers(members.values());
            fileManager.saveTransactions(transactions.values());
        }

        void addBook(String isbn, String title, String author, int copies) {
            if (books.containsKey(isbn)) {
                Book existing = books.get(isbn);
                existing.setTotalCopies(existing.getTotalCopies() + copies);
                existing.setAvailableCopies(existing.getAvailableCopies() + copies);
            } else {
                books.put(isbn, new Book(isbn, title, author, copies));
            }
        }

        void removeBook(String isbn) throws LibraryException {
            Book book = books.get(isbn);
            if (book == null) throw new LibraryException("No book found with ISBN " + isbn);
            if (book.getAvailableCopies() != book.getTotalCopies())
                throw new LibraryException("Cannot remove: some copies of this book are currently issued.");
            books.remove(isbn);
        }

        List<Book> searchBooks(String keyword) {
            String needle = keyword.toLowerCase();
            List<Book> results = new ArrayList<>();
            for (Book b : books.values()) {
                if (b.getIsbn().toLowerCase().contains(needle)
                        || b.getTitle().toLowerCase().contains(needle)
                        || b.getAuthor().toLowerCase().contains(needle)) {
                    results.add(b);
                }
            }
            return results;
        }

        List<Book> listBooks() { return new ArrayList<>(books.values()); }

        void addMember(String memberId, String name, String email) throws LibraryException {
            if (members.containsKey(memberId))
                throw new LibraryException("Member ID " + memberId + " already exists.");
            members.put(memberId, new Member(memberId, name, email));
        }

        List<Member> listMembers() { return new ArrayList<>(members.values()); }

        Member getMember(String memberId) throws LibraryException {
            Member m = members.get(memberId);
            if (m == null) throw new LibraryException("No member found with ID " + memberId);
            return m;
        }

        Transaction issueBook(String isbn, String memberId) throws LibraryException {
            Book book = books.get(isbn);
            if (book == null) throw new LibraryException("No book found with ISBN " + isbn);
            Member member = getMember(memberId);
            if (member.getOutstandingFine() > 0) {
                throw new LibraryException(member.getName() + " has an outstanding fine of $"
                        + String.format("%.2f", member.getOutstandingFine()) + ". Clear it before borrowing.");
            }
            if (!book.isAvailable())
                throw new LibraryException("\"" + book.getTitle() + "\" has no available copies right now.");

            book.setAvailableCopies(book.getAvailableCopies() - 1);
            transactionCounter++;
            String txId = "T" + String.format("%04d", transactionCounter);
            LocalDate today = LocalDate.now();
            Transaction t = new Transaction(txId, isbn, memberId, today, today.plusDays(LOAN_PERIOD_DAYS));
            transactions.put(txId, t);
            return t;
        }

        Transaction returnBook(String transactionId) throws LibraryException {
            Transaction t = transactions.get(transactionId);
            if (t == null) throw new LibraryException("No transaction found with ID " + transactionId);
            if (!t.isOpen()) throw new LibraryException("This transaction was already closed on " + t.getReturnDate());

            LocalDate today = LocalDate.now();
            t.setReturnDate(today);

            Book book = books.get(t.getIsbn());
            if (book != null) book.setAvailableCopies(book.getAvailableCopies() + 1);

            long daysLate = ChronoUnit.DAYS.between(t.getDueDate(), today);
            if (daysLate > 0) {
                double fine = daysLate * FINE_PER_DAY;
                Member member = members.get(t.getMemberId());
                if (member != null) member.addFine(fine);
            }
            return t;
        }

        void payFine(String memberId) throws LibraryException {
            getMember(memberId).clearFine();
        }

        List<Transaction> listTransactions() { return new ArrayList<>(transactions.values()); }

        List<Transaction> listOverdue() {
            List<Transaction> result = new ArrayList<>();
            LocalDate today = LocalDate.now();
            for (Transaction t : transactions.values()) {
                if (t.isOpen() && t.getDueDate().isBefore(today)) result.add(t);
            }
            return result;
        }
    }

    // ==========================================================
    //  CLI — Main entry point
    // ==========================================================
    private static final Scanner scanner = new Scanner(System.in);
    private static Library library;

    public static void main(String[] args) {
        FileManager fileManager = new FileManager("data");
        library = new Library(fileManager);

        System.out.println("=========================================");
        System.out.println(" Welcome to the Library Management System");
        System.out.println("=========================================");

        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            try {
                switch (choice) {
                    case "1": addBook(); break;
                    case "2": removeBook(); break;
                    case "3": searchBooks(); break;
                    case "4": listBooks(); break;
                    case "5": addMember(); break;
                    case "6": listMembers(); break;
                    case "7": issueBook(); break;
                    case "8": returnBook(); break;
                    case "9": payFine(); break;
                    case "10": listTransactions(); break;
                    case "11": listOverdue(); break;
                    case "0":
                        library.saveAll();
                        System.out.println("Data saved. Goodbye!");
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid option, please try again.");
                }
            } catch (LibraryException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("Error: please enter a valid number.");
            }

            if (running) library.saveAll(); // persist after every successful action
        }
        scanner.close();
    }

    private static void printMenu() {
        System.out.println("\n--- MENU ---");
        System.out.println(" 1. Add a book");
        System.out.println(" 2. Remove a book");
        System.out.println(" 3. Search books");
        System.out.println(" 4. List all books");
        System.out.println(" 5. Register a member");
        System.out.println(" 6. List all members");
        System.out.println(" 7. Issue a book");
        System.out.println(" 8. Return a book");
        System.out.println(" 9. Pay a member's fine");
        System.out.println("10. List all transactions");
        System.out.println("11. List overdue books");
        System.out.println(" 0. Save and exit");
        System.out.print("Choose an option: ");
    }

    private static String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine().trim();
    }

    private static void addBook() {
        String isbn = prompt("ISBN");
        String title = prompt("Title");
        String author = prompt("Author");
        int copies = Integer.parseInt(prompt("Number of copies"));
        library.addBook(isbn, title, author, copies);
        System.out.println("Book added/updated successfully.");
    }

    private static void removeBook() throws LibraryException {
        String isbn = prompt("ISBN of book to remove");
        library.removeBook(isbn);
        System.out.println("Book removed successfully.");
    }

    private static void searchBooks() {
        String keyword = prompt("Search keyword (title, author, or ISBN)");
        List<Book> results = library.searchBooks(keyword);
        if (results.isEmpty()) System.out.println("No matching books found.");
        else results.forEach(System.out::println);
    }

    private static void listBooks() {
        List<Book> books = library.listBooks();
        if (books.isEmpty()) System.out.println("No books in the catalog yet.");
        else books.forEach(System.out::println);
    }

    private static void addMember() throws LibraryException {
        String id = prompt("Member ID");
        String name = prompt("Name");
        String email = prompt("Email");
        library.addMember(id, name, email);
        System.out.println("Member registered successfully.");
    }

    private static void listMembers() {
        List<Member> members = library.listMembers();
        if (members.isEmpty()) System.out.println("No members registered yet.");
        else members.forEach(System.out::println);
    }

    private static void issueBook() throws LibraryException {
        String isbn = prompt("ISBN of book to issue");
        String memberId = prompt("Member ID");
        Transaction t = library.issueBook(isbn, memberId);
        System.out.println("Book issued. Transaction ID: " + t.getTransactionId()
                + " | Due date: " + t.getDueDate());
    }

    private static void returnBook() throws LibraryException {
        String txId = prompt("Transaction ID");
        Transaction t = library.returnBook(txId);
        System.out.println("Book returned on " + t.getReturnDate() + ".");
        Member member = library.getMember(t.getMemberId());
        if (member.getOutstandingFine() > 0) {
            System.out.printf("Note: %s now has an outstanding fine of $%.2f (late return).%n",
                    member.getName(), member.getOutstandingFine());
        }
    }

    private static void payFine() throws LibraryException {
        String memberId = prompt("Member ID");
        library.payFine(memberId);
        System.out.println("Fine cleared for member " + memberId + ".");
    }

    private static void listTransactions() {
        List<Transaction> transactions = library.listTransactions();
        if (transactions.isEmpty()) System.out.println("No transactions yet.");
        else transactions.forEach(System.out::println);
    }

    private static void listOverdue() {
        List<Transaction> overdue = library.listOverdue();
        if (overdue.isEmpty()) System.out.println("No overdue books. Nice!");
        else overdue.forEach(System.out::println);
    }
}

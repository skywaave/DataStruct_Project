 // Dynamic Stack Dungeon Crawler: Push → Explore → Pop → Backtrack
// DynamicStack: a stack that grows automatically when it becomes full.
// It stores items in an array and keeps track of how many items are currently in use.
class DynamicStack {
    // constructor(capacity = 2): creates a stack with a starting size.
    // We use Math.max(1, capacity) so the stack never has a size of 0 or less.
    constructor(capacity = 2) {
        this.items = new Array(Math.max(1, capacity));
        this.size = 0;
    }

    // push(value): adds an item to the top of the stack.
    // If the array is full, we double its length to make room.
    push(value) {
        if (this.size === this.items.length) this.items.length *= 2;
        this.items[this.size++] = value;
    }

    // pop(): removes and returns the most recent item from the stack.
    // If the stack is empty, it returns undefined.
    pop() {
        if (this.size === 0) return undefined;
        const value = this.items[--this.size];
        this.items[this.size] = undefined;
        return value;
    }

    // toArray(): returns only the active items in stack order.
    // This ignores empty slots left behind after removals.
    toArray() {
        return this.items.slice(0, this.size);
    }
}

// DungeonCrawler: tracks movement through rooms using a stack.
// Each move stores where you came from and where you went.
class DungeonCrawler {
    // constructor(startRoom): sets the initial room and creates a path stack.
    constructor(startRoom) {
        this.currentRoom = startRoom;
        this.path = new DynamicStack();
    }

    // explore(room): moves deeper into the dungeon and records the path.
    // The stack stores a movement object like { from: 'Entrance', to: 'Crystal Hall' }.
    explore(room) {
        this.path.push({ from: this.currentRoom, to: room });
        this.currentRoom = room;
        console.log(`PUSH → Explore ${room}`);
    }

    // backtrack(): reverses the most recent move by popping the last path entry.
    // This simulates walking back to the previous room.
    backtrack() {
        const movement = this.path.pop();
        if (!movement) {
            console.log('The entrance is the only room left.');
            return;
        }
        this.currentRoom = movement.from;
        console.log(`POP → Backtrack to ${this.currentRoom}`);
    }

    // showStatus(): prints the full room path and the current stack size.
    // It reconstructs the route from the stack data.
    showStatus() {
        const moves = this.path.toArray();
        const rooms = moves.length
            ? [moves[0].from, ...moves.map((move) => move.to)]
            : [this.currentRoom];
        console.log(`Path: ${rooms.join(' → ')} | Stack size: ${this.path.size}`);
    }
}

// Concept graphic: the newest movement sits on top and is popped first.
// This is the main idea behind a stack: Last In, First Out (LIFO).
console.log('🧭 Entrance ──push──> Crystal Hall ──push──> Cavern');
console.log('                                      🧱 TOP: Cavern (pop first)');

// Create a dungeon crawler starting at the entrance.
const crawler = new DungeonCrawler('Entrance');

// Explore several rooms. Each call pushes a new movement onto the stack.
crawler.explore('Crystal Hall');
crawler.explore('Mushroom Cavern');
crawler.explore("Dragon's Lair");

// Show the current path before backtracking.
crawler.showStatus();

// Go back one step by removing the most recent path entry.
crawler.backtrack();

// Show the updated path after backtracking.
crawler.showStatus();

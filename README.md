### Boilerplate & main game logic code is not present. These files are more interesting:

- Happiness.java -> MiniMax on MrX
- Louis.java -> MCTS on MrX
- ShortestPath.java -> Dijkstra's Algorithm implementation
- MCNode.java, MCState.java -> Helper types for the MCTS implementation



### Improvements:

Expansion: Expanding nodes using the Cartesian product of all player moves creates an excessive branching factor, making the search too wide and shallow.

Fix: Expand only based on MrX's moves; handle detective moves during simulation.

Simulation: Playouts lack realism and accuracy: MrX's double moves aren't simulated. Ticket usage isn't tracked within simulations. Frequent new MCState creation is inefficient.

Fix: Add double moves/ticket tracking to simulations; optimize state handling (e.g., mutable state).

Move Selection: pickMove logic is complex and disconnected from MCTS. It tries to reverse-engineer the best move from the best state found, leading to potential errors and inconsistencies with filtering applied post-search.

Fix: Store the move leading to each node within MCTS. Have MCTS return the best move directly. Apply filtering consistently during the search (expansion/simulation).

Scoring Efficiency: Checking detective move validity within calculateScore seems inefficient.

Fix: Optimize this check (e.g., simpler ticket check).

Minor: Use a standard library or custom Pair class instead of javafx.util.Pair.

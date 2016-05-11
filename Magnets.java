import java.util.*;
import java.io.*;

class Main {
   public static void printUsage() {
      System.out.println("Usage: java Main fileName");
      System.exit(-1);
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 1) {
         printUsage();
      }

      String[] map = getMapFromFile(args[0]);
      Board b = new Board(map);
      int stepCount = 0;
      Scanner scan = new Scanner(System.in);
      while (!b.levelCompleted()) {
         printBoard(b);

         CardinalDirection dir;
         do {
            dir = getInput(scan);
         } while (b.step(dir) == null);
         stepCount++;
      }

      printBoard(b);

      System.out.println("You won. Hurray...");
      System.out.println("Score: "+stepCount);
   }

   public static String[] getMapFromFile(String filename) throws IOException {
      ArrayList<String> map = new ArrayList<String>();
      BufferedReader reader = new BufferedReader(new FileReader(filename));
      String line;
      while ((line = reader.readLine()) != null) {
         map.add(line);
      }
      
      return map.toArray(new String[map.size()]);
   }

   public static CardinalDirection getInput(Scanner scan) {
      while (true) {
         System.out.print("[wasd q]:");
         String input = scan.nextLine().trim();
         if (input.length() == 0) {
            return null;
         }
         switch (input.charAt(0)) {
         case 'w': case 'k':
            return CardinalDirection.Up;
         case 'd': case 'l':
            return CardinalDirection.Right;
         case 's': case 'j':
            return CardinalDirection.Down;
         case 'a': case 'h':
            return CardinalDirection.Left;
         case 'q':
            System.exit(0);
         }
      }
   }

   public static void printBoard(Board board) {
      for (int row = 0; row < board.rows; row++) {
         for (int col = 0; col < board.cols; col++) {
            System.out.print(board.entities[row][col].topRow());
         }
         System.out.println();
         for (int col = 0; col < board.cols; col++) {
            if (row == board.goalRow && col == board.goalCol && board.entities[row][col] instanceof EmptyEntity) {
               System.out.print(" G ");
            } else {
               System.out.print(board.entities[row][col].midRow());
            }
         }
         System.out.println();
         for (int col = 0; col < board.cols; col++) {
            System.out.print(board.entities[row][col].botRow());
         }
         System.out.println();
      }
   }
}

class Solver {
   public static void printUsage() {
      System.out.println("Usage: java Main fileName");
      System.exit(-1);
   }

   private final static CardinalDirection[] ACTIONS = new CardinalDirection[CardinalDirection.values().length+1];
   static {
      ACTIONS[0] = null;
      System.arraycopy(CardinalDirection.values(),0,ACTIONS,1,CardinalDirection.values().length);
   }

   public static void main(String[] args) throws IOException {
      if (args.length == 0) {
         printUsage();
      }
      for (String fileName : args) {
         String[] map = Main.getMapFromFile(fileName);
         Board b = new Board(map);
         System.out.println(fileName+": "+DFS(b));
      }
   }

   private static class BoardState implements Comparable<BoardState> {
      int[] points;
      public BoardState(Board b) {
         points = new int[2*(b.mobiles.length+1)];
         points[0] = b.playerRow;
         points[1] = b.playerCol;
         for (int i = 0; i < b.mobiles.length; i++) {
            MobileEntity me = b.mobiles[i];
            points[2*(i+1)] = me.row;
            points[2*(i+1)+1] = me.col;
         }
      }
      public int compareTo(BoardState o) {
         assert(points.length == o.points.length);
         for (int i = 0; i < points.length; i++) {
            if (points[i] != o.points[i]) {
               return points[i] - o.points[i];
            }
         }
         return 0;
      }

      public boolean equals(Object o) {
         if (!(o instanceof BoardState)) {
            return false;
         }
         return compareTo((BoardState)o) == 0;
      }

      public int hashCode() {
         // Most likely cargo culting. http://stackoverflow.com/a/892640/250356
         int hash = 23;
         for (int i : points) {
            hash = hash * 31 + i;
         }
         return hash;
      }
   }

   private static class StackState {
      int actionNdx;
      List<Movement> movements;
      public StackState(int actionNdx_, List<Movement> movements_) { actionNdx=actionNdx_; movements=movements_; }
      public String toString() { return (ACTIONS[actionNdx]+"").charAt(0)+""; }

      public void undo(Board b) {
         for (int i = movements.size()-1; i >= 0; i--) {
            Movement m = movements.get(i);
            b.swap(m.r,m.c,m.r+m.dr,m.c+m.dc);
         }
         CardinalDirection dir = ACTIONS[actionNdx];
         if (dir != null) {
            int newPlayerRow = dir.reverse.nextRow(b.playerRow), newPlayerCol = dir.reverse.nextCol(b.playerCol);
            b.swap(b.playerRow,b.playerCol,newPlayerRow,newPlayerCol);
            b.playerRow = newPlayerRow;
            b.playerCol = newPlayerCol;
         }
      }
   }

   private static void unwind(ArrayList<StackState> stack, Board b) {
      while (stack.size() > 0 && stack.get(stack.size()-1).actionNdx == ACTIONS.length-1) {
         stack.remove(stack.size()-1).undo(b);
      }
   }

   public static int nextValidAction(int prevActionNdx, Board b) {
      int nextActionNdx = prevActionNdx+1;
      for ( ; nextActionNdx < ACTIONS.length; nextActionNdx++) {
         CardinalDirection action = ACTIONS[nextActionNdx];
         if (action == null || b.movePlayer(action)) {
            break;
         }
      }
      return nextActionNdx;
   }

   public static ArrayList<StackState> DFS(Board board) {
      ArrayList<StackState> stack = new ArrayList<StackState>();
      Collection<BoardState> visited = new HashSet<BoardState>();

      visited.add(new BoardState(board));

      int prospectiveActionNdx = 0;
      while (true) {
         CardinalDirection action = ACTIONS[prospectiveActionNdx];
         if (action == null || board.movePlayer(action)) {
            StackState ss = new StackState(prospectiveActionNdx, board.step());
            BoardState bs = new BoardState(board);
            stack.add(ss);
            if (visited.contains(bs)) {
               prospectiveActionNdx = ACTIONS.length;
            } else {
               prospectiveActionNdx = 0;
               visited.add(bs);
               if (board.levelCompleted()) {
                  break;
               }
            }
         } else {
            prospectiveActionNdx++;
         }

         if (prospectiveActionNdx == ACTIONS.length) {
            unwind(stack, board);
            if (stack.size() == 0) {
               break;
            }
            StackState s = stack.remove(stack.size()-1);
            s.undo(board);
            prospectiveActionNdx = s.actionNdx + 1;
         }
      }

      return stack;
   }
}

class Board {
   Entity[][] entities;
   int rows, cols;
   int playerRow = -1, playerCol = -1;
   int goalRow = -1, goalCol = -1;
   MobileEntity[] mobiles;

   private final static WallEntity WALL = new WallEntity(Force.Block, Force.Block, Force.Block, Force.Block);

   public Board(String[] board) {
      char[][] charBoard = new char[board.length][];
      for (int i = 0; i < board.length; i++) {
         charBoard[i] = board[i].toCharArray();
      }
      init(charBoard);
   }
   public Board(char[][] board) {
      init(board);
   }

   private void init(char[][] board) {
      if (board.length % 3 != 0 || board[0].length % 3 != 0) {
         throw new IllegalArgumentException();
      }
      for (char[] row : board) {
         if (row.length != board[0].length) {
            throw new IllegalArgumentException();
         }
      }
      
      rows = board.length/3;
      cols = board[0].length/3;
      entities = new Entity[rows][cols];

      char[][] tmp = new char[3][3];
      ArrayList<MobileEntity> mobilesList = new ArrayList<MobileEntity>();
      for (int row = 0; row < rows; row++) {
         for (int col = 0; col < cols; col++) {
            for (int i = 0; i < 3; i++) {
               System.arraycopy(board[3*row+i], 3*col, tmp[i], 0, 3);
            }
            Entity e = Entity.createEntity(tmp);
            entities[row][col] = e;
            if (tmp[1][1] == 'G') {
               goalRow = row;
               goalCol = col;
            } else if (tmp[1][1] == 'P') {
               playerRow = row;
               playerCol = col;
            } else if (e instanceof MobileEntity) {
               MobileEntity me = (MobileEntity)e;
               me.row = row;
               me.col = col;
               mobilesList.add(me);
            }
         }
      }
      if (goalRow == -1 || playerRow == -1) {
         throw new IllegalArgumentException();
      }

      mobiles = mobilesList.toArray(new MobileEntity[mobilesList.size()]);

      if (!isWalled()) {
         int newRows = rows+2, newCols = cols+2;
         Entity[][] newEntities = new Entity[newRows][newCols];

         Arrays.fill(newEntities[0], WALL);
         Arrays.fill(newEntities[newRows-1], WALL);
         for (int row = 1; row < newRows-1; row++) {
            System.arraycopy(entities[row-1], 0, newEntities[row], 1, cols);
            newEntities[row][0] = newEntities[row][newCols-1] = WALL;
         }

         rows = newRows;
         cols = newCols;
         entities = newEntities;
         goalRow++;
         goalCol++;
         playerRow++;
         playerCol++;
         for (MobileEntity me : mobiles) {
            me.row++;
            me.col++;
         }
      }
   }

   private boolean isWalled() {
      for (int row = 0; row < rows; row++) {
         if (!(entities[row][0] instanceof WallEntity && entities[row][cols-1] instanceof WallEntity)) {
            return false;
         }
      }
      for (int col = 0; col < cols; col++) {
         if (!(entities[0][col] instanceof WallEntity && entities[rows-1][col] instanceof WallEntity)) {
            return false;
         }
      }
      return true;
   }

   public boolean levelCompleted() {
      return playerRow == goalRow && playerCol == goalCol;
   }

   private Force forceFromDirection(CardinalDirection dir, int r, int c) {
      Force out = Force.Passthrough;
      do {
         r = dir.nextRow(r);
         c = dir.nextCol(c);
         if (!(Utils.bound(0,r,rows) && Utils.bound(0,c,cols))) {
            break;
         }
         out = entities[r][c].forceExertedToDirection(dir.reverse);
      } while (out == Force.Passthrough);
      return out;
   }

   private MotionType motionTypeFromDirection(CardinalDirection dir, int r, int c, Force base) {
      if (base == Force.Passthrough || base == Force.Block) {
         return MotionType.None;
      }
      Force from = forceFromDirection(dir, r, c);
      if (from == Force.Passthrough || from == Force.Block) {
         return MotionType.None;
      }
      //System.out.printf("d:%s b:%s f:%s r:%d c:%d\n",dir,base,from,r,c);
      return from == base ? MotionType.Repel : MotionType.Attract;
   }

   private static enum MotionType {
      Attract(1), None(0), Repel(-1);

      MotionType reverse;
      int val;

      MotionType(int val_) { val=val_; }

      static {
         Attract.reverse = Repel;
         None.reverse = None;
         Repel.reverse = Attract;
      }

      public MotionType combine(MotionType o) {
         if (o == None) return this;
         if (this == None) return o;
         return o == this ? this : None;
      }
   }

   public boolean isEmpty(int r, int c) {
      return Utils.bound(0, r, rows) && Utils.bound(0, c, cols) && entities[r][c] instanceof EmptyEntity;
   }

   public void swap(int r1, int c1, int r2, int c2) {
      Entity tmp = entities[r1][c1];
      entities[r1][c1] = entities[r2][c2];
      entities[r2][c2] = tmp;
      if (entities[r1][c1] instanceof MobileEntity) {
         MobileEntity me = (MobileEntity)entities[r1][c1];
         me.row = r1;
         me.col = c1;
      }
      if (entities[r2][c2] instanceof MobileEntity) {
         MobileEntity me = (MobileEntity)entities[r2][c2];
         me.row = r2;
         me.col = c2;
      }
   }

   public List<Movement> step(CardinalDirection dir) {
      if (dir != null && !movePlayer(dir)) {
         return null;
      }
      return step();
   }

   public boolean movePlayer(CardinalDirection dir) {
      int newRow = dir.nextRow(playerRow), newCol = dir.nextCol(playerCol);
      if (!isEmpty(newRow,newCol)) {
         return false;
      }

      swap(playerRow,playerCol,newRow,newCol);
      playerRow = newRow;
      playerCol = newCol;

      return true;
   }

   public List<Movement> step() {
      int[][] drs = new int[rows][cols], dcs = new int[rows][cols];
      {
         for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
               Entity e = entities[r][c];
               if (!(e instanceof MobileEntity)) {
                  continue;
               }
               MobileEntity me = (MobileEntity)e;
               MotionType uMotion = motionTypeFromDirection(CardinalDirection.Up, r, c, me.u);
               MotionType rMotion = motionTypeFromDirection(CardinalDirection.Right, r, c, me.r);
               MotionType dMotion = motionTypeFromDirection(CardinalDirection.Down, r, c, me.d);
               MotionType lMotion = motionTypeFromDirection(CardinalDirection.Left, r, c, me.l);

               drs[r][c] = dMotion.combine(uMotion.reverse).val;
               dcs[r][c] = rMotion.combine(lMotion.reverse).val;
               //System.out.printf("u:%s r:%s d:%s l:%s\n", uMotion, rMotion, dMotion, lMotion);
               //System.out.println(drs[r][c]+" "+dcs[r][c]);
            }
         }
      }
      ArrayList<Movement> movements = new ArrayList<Movement>();
      {
         for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
               int dr = drs[r][c], dc = dcs[r][c];
               if ((dr != 0 || dc != 0) && isEmpty(r+dr,c+dc)) {
                  movements.add(new Movement(entities[r][c], r, c, dr, dc));
                  swap(r,c,r+dr,c+dc);
               }
            }
         }
      }
      return movements;
   }
}

class Utils {
   public static boolean bound(int lo, int mid, int hi) {
      return lo <= mid && mid < hi;
   }
}

class Movement {
   Entity entity;
   int r, c;
   int dr, dc;
   public Movement(Entity entity_, int r_, int c_, int dr_, int dc_) { entity=entity_; r=r_; c=c_; dr=dr_; dc=dc_; }
   public String toString() { return String.format("(%d%+d,%d%+d)",r,dr,c,dc); }
}

enum CardinalDirection {
   Up(-1,0), Right(0,1), Down(1,0), Left(0,-1);
   
   int dr, dc;
   CardinalDirection reverse;

   CardinalDirection(int dr_, int dc_) { dr=dr_; dc=dc_; }

   static {
      Up.reverse = Down;
      Right.reverse = Left;
      Down.reverse = Up;
      Left.reverse = Right;
   }

   public <T> T chooseURDL(T u, T r, T d, T l) {
      switch (this) {
      case Up:
         return u;
      case Right:
         return r;
      case Down:
         return d;
      case Left:
         return l;
      }
      throw new IllegalArgumentException();
   }

   public int nextRow(int currR) { return currR + dr; }
   public int nextCol(int currC) { return currC + dc; }
}

enum Force {
   Plus('+'), Minus('-'), Passthrough('_'), Block(' ');

   public static Force fromChar(char c) {
      switch (c) {
      case '+':
         return Plus;
      case '-':
         return Minus;
      case '_':
         return Passthrough;
      case '0':
      case ' ':
         return Block;
      }
      throw new IllegalArgumentException();
   }

   char repr;
   Force(char repr_) { repr=repr_; }
}

class Point2D {
   int r, c;
   public Point2D(int r_, int c_) { r=r_; c=c_; }
}

abstract class Entity {
   public static Entity createEntity(char[][] repr) {
      if (repr.length != 3) {
         throw new IllegalArgumentException();
      }
      for (char[] s : repr) {
         if (s.length != 3) {
            throw new IllegalArgumentException();
         }
      }

      switch (repr[1][1]) {
      case '_':
      case 'G':
         return new EmptyEntity();
      case 'X':
         return new UnpathableEntity();
      case 'W':
         return new WallEntity(Force.fromChar(repr[0][1]), Force.fromChar(repr[1][2]), Force.fromChar(repr[2][1]), Force.fromChar(repr[1][0]));
      case 'M':
         return new MobileEntity(Force.fromChar(repr[0][1]), Force.fromChar(repr[1][2]), Force.fromChar(repr[2][1]), Force.fromChar(repr[1][0]));
      case 'P':
         return new PlayerEntity(Force.fromChar(repr[0][1]), Force.fromChar(repr[1][2]), Force.fromChar(repr[2][1]), Force.fromChar(repr[1][0]));
      }
      throw new IllegalArgumentException();
   }

   Force forceExertedToDirection(CardinalDirection dir) { return Force.Passthrough; }
   boolean isMobile() { return false; }
   boolean isPlayer() { return false; }

   char midChar;
   protected Entity(char midChar_) { midChar=midChar_; }

   public String topRow() { return "   "; }
   public String midRow() { return " "+midChar+" "; }
   public String botRow() { return "   "; }

   @Override
   public String toString() { return ""+midChar; }
}

class EmptyEntity extends Entity {
   public EmptyEntity() { super('_'); }
}

class UnpathableEntity extends Entity {
   public UnpathableEntity() { super('X'); }
}

abstract class ForcedEntity extends Entity {
   Force u, r, d, l;

   public ForcedEntity(Force u_, Force r_, Force d_, Force l_, char midChar) { super(midChar); u=u_; r=r_; d=d_; l=l_; }
   @Override
   Force forceExertedToDirection(CardinalDirection dir) { return dir.chooseURDL(u,r,d,l); }

   public String topRow() { return " "+u.repr+" "; }
   public String midRow() { return ""+l.repr+midChar+r.repr; }
   public String botRow() { return " "+d.repr+" "; }
}

class WallEntity extends ForcedEntity {
   public WallEntity(Force u, Force r, Force d, Force l) { super(u,r,d,l,'W'); }
}

class PlayerEntity extends ForcedEntity {
   public PlayerEntity(Force u, Force r, Force d, Force l) { super(u,r,d,l,'P'); }
   @Override
   boolean isPlayer() { return true; }
}

class MobileEntity extends ForcedEntity {
   int row = -1, col = -1;
   public MobileEntity(Force u, Force r, Force d, Force l) { super(u,r,d,l,'M'); }
   @Override
   boolean isMobile() { return true; }
}

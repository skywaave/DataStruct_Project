import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Standalone Swing prototype. Run this class instead of the console Main. */
public class ArcadeMacroRecorder extends JFrame {
    private static final Color BG = new Color(14, 23, 37);
    private static final Color PANEL = new Color(25, 40, 57);
    private static final Color GREEN = new Color(100, 239, 169);
    private static final Color YELLOW = new Color(255, 214, 90);
    private static final int[] KEYS = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S,
        KeyEvent.VK_D, KeyEvent.VK_J, KeyEvent.VK_K, KeyEvent.VK_L,
        KeyEvent.VK_U, KeyEvent.VK_I, KeyEvent.VK_O};
    private final Set<Integer> held = new HashSet<>();
    private final DynamicStack<Segment> segments = new DynamicStack<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(
        new Object[]{"Start frame", "Input", "Frames"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JLabel state = new JLabel("READY  /  60 FPS timeline");
    private final JLabel feedback = new JLabel("LIVE INPUT / Ready when you are");
    private final StickPanel stick = new StickPanel();
    private final JButton record = button("Record"), stop = button("Stop");
    private final JButton play = button("Play"), undo = button("Undo"), clear = button("Clear");
    private boolean recording, playing;
    private long epoch;
    private int currentMask, currentStart, playIndex;
    private ArrayList<Segment> playback = new ArrayList<>();
    private final javax.swing.Timer ticker;
    private final HistoryPanel history = new HistoryPanel();
    private final long entranceStart = System.nanoTime();
    private boolean intro = true;
    private float entrance;
    private int playbackEnd;
    private final KeyEventDispatcher introKeys = e -> {
        if (intro && e.getID() == KeyEvent.KEY_PRESSED) { finishIntro(); return true; }
        return intro;
    };

    public ArcadeMacroRecorder() {
        super("Input Lab / Macro Recorder");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        StagePanel root = new StagePanel();
        root.setLayout(new BorderLayout(18,18));
        root.setBackground(BG); root.setBorder(BorderFactory.createEmptyBorder(24,24,24,24));
        setContentPane(root);
        JLabel title = new JLabel("INPUT LAB");
        title.setForeground(GREEN); title.setFont(new Font("Serif",Font.BOLD,38));
        JPanel heading = new JPanel(new GridLayout(2,1,0,8)); heading.setOpaque(false);
        heading.add(title); state.setForeground(Color.WHITE); heading.add(state);
        root.add(heading,BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0,16)); center.setOpaque(false);
        center.add(stick,BorderLayout.CENTER);
        history.setPreferredSize(new Dimension(800,175));
        center.add(history,BorderLayout.SOUTH); root.add(center,BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0,12)); bottom.setOpaque(false);
        feedback.setForeground(YELLOW); feedback.setFont(new Font("SansSerif",Font.BOLD,18));
        bottom.add(feedback,BorderLayout.NORTH);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT,9,0)); controls.setOpaque(false);
        for (JButton b : new JButton[]{record,stop,play,undo,clear}) controls.add(b);
        bottom.add(controls,BorderLayout.CENTER);
        JLabel help = new JLabel("WASD: direction  |  J K L: punches  |  U I O: kicks  |  Inputs work while this window is active");
        help.setForeground(new Color(179,197,216)); bottom.add(help,BorderLayout.SOUTH);
        root.add(bottom,BorderLayout.SOUTH);
        record.addActionListener(e -> startRecording()); stop.addActionListener(e -> stopSession());
        play.addActionListener(e -> startPlayback()); undo.addActionListener(e -> undoSegment());
        clear.addActionListener(e -> { segments.clear(); refreshTable(); feedback.setText("RECORDING CLEARED"); state.setText("READY / 60 FPS timeline"); updateControls(); });
        installKeys(root);
        addWindowFocusListener(new WindowAdapter() {
            public void windowLostFocus(WindowEvent e) {
                // Stop at the focus boundary so keys released elsewhere cannot get stuck.
                stopSession(); held.clear(); stick.mask=0; stick.repaint();
            }
        });
        JPanel shield = new JPanel(); shield.setOpaque(false);
        shield.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { finishIntro(); }
        });
        setGlassPane(shield); shield.setVisible(true);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(introKeys);
        ticker = new javax.swing.Timer(16,e -> {
            if (intro) {
                entrance = Math.min(1f,(System.nanoTime()-entranceStart)/1_400_000_000f);
                if (entrance >= 1) finishIntro();
            }
            tick(); root.repaint();
        }); ticker.start();
        addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                ticker.stop();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(introKeys);
            }
        });
        setMinimumSize(new Dimension(1000,720)); setSize(1120,800);
        setLocationRelativeTo(null); updateControls();
    }
    private static JButton button(String text) {
        JButton b = new JButton(text); b.setFocusable(false);
        b.setBackground(PANEL); b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif",Font.BOLD,13));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(72,99,112)),
            BorderFactory.createEmptyBorder(11,22,11,22)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    private void installKeys(JPanel root) {
        for (int key : KEYS) {
            String name = KeyEvent.getKeyText(key);
            for (boolean release : new boolean[]{false,true}) {
                String action = name + (release ? " up" : " down");
                root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(key,0,release),action);
                root.getActionMap().put(action,new AbstractAction() {
                    public void actionPerformed(ActionEvent e) { changeKey(key,release); }
                });
            }
        }
    }
    private int mask() {
        int m=0; for(int i=0;i<KEYS.length;i++) if(held.contains(KEYS[i])) m|=1<<i;
        return m;
    }
    private int frame() {
        return (int)Math.min(Integer.MAX_VALUE,(System.nanoTime()-epoch)*60L/1_000_000_000L);
    }
    private void changeKey(int key, boolean release) {
        if(playing || intro) return;
        boolean changed = release ? held.remove(key) : held.add(key);
        if(!changed) return; // Ignore keyboard auto-repeat.
        int next=mask();
        if(recording && next!=currentMask) {
            int now=frame(); commitCurrent(now); currentMask=next; currentStart=now;
        }
        stick.mask=next; stick.repaint();
    }
    private void startRecording() {
        segments.clear(); tableModel.setRowCount(0);
        epoch=System.nanoTime(); currentStart=0; currentMask=mask();
        recording=true; playing=false;
        feedback.setText("RECORDING / MAKE YOUR MOVE"); updateControls();
    }
    private void commitCurrent(int end) {
        int duration=end-currentStart;
        // Preserve zero-frame transitions: multiple events may occur in one frame.
        if(duration>0 || currentMask!=mask()) {
            segments.push(new Segment(currentMask,duration));
            appendRow(currentStart,currentMask,duration);
        }
    }
    private void stopSession() {
        if(recording) { commitCurrent(frame()); recording=false; }
        playing=false; held.clear(); stick.mask=0; stick.repaint();
        state.setText("STOPPED  /  "+segments.size()+" segments  /  capacity "+segments.capacity());
        updateControls();
    }
    private void startPlayback() {
        if(segments.isEmpty()) return;
        playback=segments.snapshot(); held.clear(); playing=true; recording=false;
        epoch=System.nanoTime(); playIndex=0; playbackEnd=playback.get(0).frames;
        feedback.setText("PLAYBACK / WATCH THE CONTROLLER"); updateControls(); tick();
    }
    private void tick() {
        if(recording) state.setText("RECORDING  /  FRAME "+frame()+"  /  "+segments.size()+" completed segments");
        if(playing) {
            int now=frame();
            while(playIndex<playback.size() && now>=playbackEnd) {
                playIndex++;
                if(playIndex<playback.size()) playbackEnd+=playback.get(playIndex).frames;
            }
            if(playIndex>=playback.size()) {
                stopSession(); feedback.setText("PLAYBACK COMPLETE");
            } else {
                stick.mask=playback.get(playIndex).mask; stick.repaint();
                state.setText("PLAYBACK  /  FRAME "+now+"  /  SEGMENT "+(playIndex+1));
            }
        }
    }
    private void undoSegment() {
        if(segments.isEmpty()) return;
        Segment removed=segments.pop(); refreshTable();
        feedback.setText("REMOVED / "+describe(removed.mask)+" / "+removed.frames+"f");
        state.setText("READY  /  "+segments.size()+" segments  /  capacity "+segments.capacity());
        updateControls();
    }
    private void appendRow(int start,int mask,int frames) { tableModel.addRow(new Object[]{start,describe(mask),frames}); }
    private void refreshTable() {
        tableModel.setRowCount(0); int start=0;
        for(Segment s:segments.snapshot()) { appendRow(start,s.mask,s.frames); start+=s.frames; }
    }
    private void updateControls() {
        record.setEnabled(!intro&&!recording&&!playing); stop.setEnabled(recording||playing);
        play.setEnabled(!recording&&!playing&&!segments.isEmpty());
        undo.setEnabled(!recording&&!playing&&!segments.isEmpty()); clear.setEnabled(!intro&&!recording&&!playing);
    }
    private static boolean has(int m,int i) { return (m&(1<<i))!=0; }
    static String describe(int m) {
        int x=(has(m,3)?1:0)-(has(m,1)?1:0);
        int y=(has(m,2)?1:0)-(has(m,0)?1:0);
        String[] dirs={"7","8","9","4","N","6","1","2","3"};
        StringBuilder s=new StringBuilder(dirs[(y+1)*3+x+1]);
        String[] attacks={"LP","MP","HP","LK","MK","HK"};
        for(int i=4;i<10;i++) if(has(m,i)) s.append(" + ").append(attacks[i-4]);
        return s.toString();
    }
    static final class Segment {
        final int mask,frames;
        Segment(int mask,int frames) { this.mask=mask; this.frames=frames; }
    }
    /** Our own resizing array stack: push/pop at the end, oldest at index zero. */
    static final class DynamicStack<T> {
        private Object[] data=new Object[8]; private int size;
        void push(T item) { if(size==data.length) data=Arrays.copyOf(data,data.length*2); data[size++]=item; }
        @SuppressWarnings("unchecked") T pop() {
            if(isEmpty()) throw new EmptyStackException();
            T result=(T)data[--size]; data[size]=null; return result;
        }
        @SuppressWarnings("unchecked") T get(int index) {
            if(index<0 || index>=size) throw new IndexOutOfBoundsException();
            return (T)data[index];
        }
        boolean isEmpty() { return size==0; }
        int size() { return size; }
        int capacity() { return data.length; }
        void clear() { Arrays.fill(data,0,size,null); size=0; }
        @SuppressWarnings("unchecked") ArrayList<T> snapshot() {
            ArrayList<T> copy=new ArrayList<>();
            for(int i=0;i<size;i++) copy.add((T)data[i]); return copy;
        }
    }
    // PRESENTATION: independent of stored segments and the stack.
    private void finishIntro() {
        intro=false; entrance=1; getGlassPane().setVisible(false); updateControls();
    }
    private static float ease(float t) { t=Math.max(0,Math.min(1,t)); return 1-(1-t)*(1-t)*(1-t); }
    private static Graphics2D smooth(Graphics g) {
        Graphics2D c=(Graphics2D)g.create();
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        c.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return c;
    }
    private final class StagePanel extends JPanel {
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); Graphics2D g=smooth(graphics);
            g.setPaint(new GradientPaint(0,0,BG,getWidth(),getHeight(),new Color(16,43,48)));
            g.fillRect(0,0,getWidth(),getHeight());
            g.setColor(new Color(45,83,88,60));
            for(int x=-getHeight();x<getWidth();x+=90) g.drawLine(x,0,x+getHeight(),getHeight());
            g.setColor(YELLOW); g.fillRect(24,16,50,3); g.dispose();
        }
        protected void paintChildren(Graphics graphics) {
            float reveal=intro?ease((entrance-.42f)/.58f):1;
            Graphics2D g=smooth(graphics);
            g.setComposite(AlphaComposite.SrcOver.derive(reveal));
            g.translate(0,(int)(24*(1-reveal))); super.paintChildren(g); g.dispose();
            if(intro) {
                g=smooth(graphics);
                float settle=ease((entrance-.48f)/.52f);
                g.setComposite(AlphaComposite.SrcOver.derive(1-settle));
                int x=(int)((getWidth()/2-215)*(1-settle)+24*settle);
                int y=(int)((getHeight()/2-30)*(1-settle)+60*settle);
                g.setColor(GREEN); g.setFont(new Font("Serif",Font.BOLD,70));
                Shape old=g.getClip();
                g.clipRect(x-5,y-90,(int)(500*ease(entrance/.45f)),110);
                g.drawString("INPUT LAB",x,y); g.setClip(old);
                g.setFont(new Font("SansSerif",Font.PLAIN,14)); g.setColor(Color.WHITE);
                g.drawString("M A C R O   R E C O R D E R",x+3,y+40);
                int sweep=(int)((getWidth()+400)*ease(entrance/.65f))-200;
                g.setColor(YELLOW);g.setStroke(new BasicStroke(3));
                g.drawLine(sweep-150,getHeight(),sweep+220,0);
                g.setColor(new Color(165,184,193));
                g.drawString("Click or press any key to skip",24,getHeight()-24);g.dispose();
            }
        }
    }
    private static int direction(int mask) {
        int x=(has(mask,3)?1:0)-(has(mask,1)?1:0);
        int y=(has(mask,2)?1:0)-(has(mask,0)?1:0);
        return (y+1)*3+x+1;
    }
    private static String symbol(int mask) {
        String[] arrows={"↖","↑","↗","←","○","→","↙","↓","↘"};
        return arrows[direction(mask)];
    }
    private final class HistoryPanel extends JPanel {
        private int previousCount;
        private long movedAt;
        HistoryPanel() { setOpaque(false); }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g=smooth(graphics);
            int width=getWidth();
            g.setColor(new Color(8,17,27,220));g.fillRoundRect(0,0,width,getHeight(),18,18);
            g.setColor(new Color(66,94,109));g.drawRoundRect(0,0,width-1,getHeight()-1,18,18);
            g.setFont(new Font("SansSerif",Font.PLAIN,14));g.setColor(Color.WHITE);
            g.drawString("C O M M A N D   H I S T O R Y",20,27);
            int count=segments.size()+(recording?1:0);
            int visible=Math.max(1,(width-40)/115);
            int last=playing?playIndex:count-1;
            int first=Math.max(0,last-visible+1);
            if(count!=previousCount) { previousCount=count; movedAt=System.nanoTime(); }
            float shift=1-ease((System.nanoTime()-movedAt)/160_000_000f);
            for(int i=first;i<=last && i<count;i++) {
                Segment s=i==segments.size()?new Segment(currentMask,Math.max(0,frame()-currentStart)):segments.get(i);
                int x=20+(i-first)*115;
                // Animate older tiles only: the current input always appears immediately.
                if(i<last && first>0) x+=(int)(15*shift);
                boolean active=i==last&&(recording||playing);
                Color accent=has(s.mask,4)||has(s.mask,5)||has(s.mask,6)||has(s.mask,7)||has(s.mask,8)||has(s.mask,9)?YELLOW:GREEN;
                g.setColor(active?new Color(43,64,62):new Color(19,31,43));g.fillRoundRect(x,62,104,75,10,10);
                g.setColor(active?accent:new Color(59,79,95));g.drawRoundRect(x,62,104,75,10,10);
                g.setColor(active?accent:new Color(174,189,201));g.setFont(new Font("Monospaced",Font.BOLD,13));
                g.drawString(s.frames+"f",x+8,52);
                g.setFont(new Font("Dialog",Font.BOLD,27));g.drawString(symbol(s.mask),x+9,95);
                String attacks=describe(s.mask); int plus=attacks.indexOf(" + ");
                attacks=plus<0?"":attacks.substring(plus+3).replace(" + "," ");
                g.setFont(new Font("SansSerif",Font.BOLD,attacks.length()>12?9:12));g.drawString(attacks,x+8,122);
            }
            if(count==0) {g.setColor(new Color(158,180,191));g.drawString("Press Record, then perform an input sequence.",20,90);}
            g.setFont(new Font("SansSerif",Font.PLAIN,11));g.setColor(new Color(158,180,191));
            g.drawString("60 FPS simulation  /  neutral time is recorded  /  latest segment at right",20,157);g.dispose();
        }
    }
    private final class StickPanel extends JPanel {
        int mask;
        private final float[] glow=new float[6];
        private long lastPaint=System.nanoTime();
        StickPanel() { setOpaque(false); }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g=smooth(graphics);
            double scale=Math.min(getWidth()/950.0,getHeight()/300.0);
            g.translate((getWidth()-950*scale)/2,(getHeight()-300*scale)/2); g.scale(scale,scale);
            g.setColor(new Color(16,29,41,225));g.fillRoundRect(0,0,950,300,24,24);
            g.setColor(new Color(59,89,103));g.drawRoundRect(0,0,949,299,24,24);
            g.setFont(new Font("SansSerif",Font.BOLD,13));g.setColor(GREEN);g.drawString("01 / LIVE CONTROLLER",28,35);
            int cx=190,cy=170;
            g.setColor(new Color(37,56,69));g.fillOval(cx-90,cy-90,180,180);
            g.setColor(new Color(69,95,102));g.drawOval(cx-90,cy-90,180,180);
            int dx=((has(mask,3)?1:0)-(has(mask,1)?1:0))*43;
            int dy=((has(mask,2)?1:0)-(has(mask,0)?1:0))*43;
            g.setStroke(new BasicStroke(11));g.setColor(new Color(111,137,142));g.drawLine(cx,cy,cx+dx,cy+dy);
            g.setColor(dx!=0||dy!=0?GREEN:new Color(212,224,220));g.fillOval(cx+dx-31,cy+dy-31,62,62);
            g.setColor(BG);g.setFont(new Font("Dialog",Font.BOLD,23));g.drawString(symbol(mask),cx+dx-10,cy+dy+9);
            double dt=(System.nanoTime()-lastPaint)/1e9;lastPaint=System.nanoTime();
            String[] names={"LP","MP","HP","LK","MK","HK"};
            String[] binds={"J","K","L","U","I","O"};
            for(int i=0;i<6;i++) {
                int x=475+(i%3)*125,y=75+(i/3)*105;
                boolean down=has(mask,i+4);glow[i]=down?1:Math.max(0,glow[i]-(float)(dt*5));
                Color c=i%3==0?GREEN:i%3==1?new Color(107,195,241):YELLOW;
                if(glow[i]>0) {g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),(int)(55*glow[i])));g.fillOval(x-8,y-8,88,88);}
                g.setColor(down?c:new Color(30,47,62));g.fillOval(x,y,72,72);
                g.setStroke(new BasicStroke(2));g.setColor(c);g.drawOval(x,y,72,72);
                g.setColor(down?BG:Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,19));g.drawString(names[i],x+23,y+43);
                g.setFont(new Font("Monospaced",Font.PLAIN,12));g.setColor(new Color(174,194,201));g.drawString(binds[i],x+32,y+90);
            }
            g.setFont(new Font("SansSerif",Font.PLAIN,12));g.setColor(new Color(174,194,201));g.drawString("W / A / S / D",145,283);g.dispose();
        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ArcadeMacroRecorder().setVisible(true));
    }
}

package org.example.client;

import org.example.model.DrawingMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * GUI for the whiteboard application with drawing capabilities
 */
public class WhiteboardGUI extends JFrame {
    private WhiteboardClient client;
    private DrawingPanel drawingPanel;
    private JLabel statusLabel;
    private JLabel userCountLabel;
    private JComboBox<String> colorComboBox;
    private JSpinner strokeWidthSpinner;
    private JToggleButton drawModeButton, textModeButton, eraseModeButton;
    private ButtonGroup modeGroup;
    
    // Drawing state
    private Color currentColor = Color.BLACK;
    private int currentStrokeWidth = 2;
    private DrawingMode currentMode = DrawingMode.DRAW;
    
    private enum DrawingMode {
        DRAW, TEXT, ERASE
    }
    
    public WhiteboardGUI(WhiteboardClient client) {
        this.client = client;
        initializeGUI();
        setupEventHandlers();
    }
    
    private void initializeGUI() {
        setTitle("Live Whiteboard - " + client.getUsername());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Create drawing panel
        drawingPanel = new DrawingPanel();
        JScrollPane scrollPane = new JScrollPane(drawingPanel);
        scrollPane.setPreferredSize(new Dimension(800, 600));
        add(scrollPane, BorderLayout.CENTER);
        
        // Create toolbar
        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);
        
        // Create status bar
        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);
        
        pack();
        setLocationRelativeTo(null);
    }
    
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBorder(BorderFactory.createEtchedBorder());
        
        // Mode buttons
        modeGroup = new ButtonGroup();
        
        drawModeButton = new JToggleButton("Draw", true);
        textModeButton = new JToggleButton("Text");
        eraseModeButton = new JToggleButton("Erase");
        
        modeGroup.add(drawModeButton);
        modeGroup.add(textModeButton);
        modeGroup.add(eraseModeButton);
        
        toolbar.add(new JLabel("Mode:"));
        toolbar.add(drawModeButton);
        toolbar.add(textModeButton);
        toolbar.add(eraseModeButton);
        
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        
        // Color selection
        toolbar.add(new JLabel("Color:"));
        String[] colors = {"Black", "Red", "Blue", "Green", "Yellow", "Orange", "Purple", "Pink"};
        colorComboBox = new JComboBox<>(colors);
        toolbar.add(colorComboBox);
        
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        
        // Stroke width
        toolbar.add(new JLabel("Stroke:"));
        strokeWidthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        toolbar.add(strokeWidthSpinner);
        
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        
        // Action buttons
        JButton clearButton = new JButton("Clear All");
        toolbar.add(clearButton);
        
        return toolbar;
    }
    
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        
        statusLabel = new JLabel("Connected to server");
        userCountLabel = new JLabel("Users: 1");
        
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(userCountLabel, BorderLayout.EAST);
        
        return statusBar;
    }
    
    private void setupEventHandlers() {
        // Mode change listeners
        drawModeButton.addActionListener(e -> currentMode = DrawingMode.DRAW);
        textModeButton.addActionListener(e -> currentMode = DrawingMode.TEXT);
        eraseModeButton.addActionListener(e -> currentMode = DrawingMode.ERASE);
        
        // Color change listener
        colorComboBox.addActionListener(e -> {
            String colorName = (String) colorComboBox.getSelectedItem();
            currentColor = getColorFromName(colorName);
        });
        
        // Stroke width change listener
        strokeWidthSpinner.addChangeListener(e -> {
            currentStrokeWidth = (Integer) strokeWidthSpinner.getValue();
        });
        
        // Clear button
        Component[] components = ((JPanel) getContentPane().getComponent(1)).getComponents();
        for (Component comp : components) {
            if (comp instanceof JButton && ((JButton) comp).getText().equals("Clear All")) {
                ((JButton) comp).addActionListener(e -> clearWhiteboard());
                break;
            }
        }
        
        // Window closing event
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.disconnect();
                System.exit(0);
            }
        });
    }
    
    private Color getColorFromName(String colorName) {
        switch (colorName.toLowerCase()) {
            case "red": return Color.RED;
            case "blue": return Color.BLUE;
            case "green": return Color.GREEN;
            case "yellow": return Color.YELLOW;
            case "orange": return Color.ORANGE;
            case "purple": return new Color(128, 0, 128);
            case "pink": return Color.PINK;
            default: return Color.BLACK;
        }
    }
    
    private String getColorName(Color color) {
        if (color.equals(Color.RED)) return "Red";
        if (color.equals(Color.BLUE)) return "Blue";
        if (color.equals(Color.GREEN)) return "Green";
        if (color.equals(Color.YELLOW)) return "Yellow";
        if (color.equals(Color.ORANGE)) return "Orange";
        if (color.equals(new Color(128, 0, 128))) return "Purple";
        if (color.equals(Color.PINK)) return "Pink";
        return "Black";
    }
    
    private void clearWhiteboard() {
        DrawingMessage clearMessage = new DrawingMessage(DrawingMessage.MessageType.CLEAR, client.getUsername());
        client.sendMessage(clearMessage);
        drawingPanel.clear();
    }
    
    /**
     * Handle messages received from the server
     */
    public void handleServerMessage(DrawingMessage message) {
        switch (message.getType()) {
            case DRAW:
                drawingPanel.drawLine(message.getX1(), message.getY1(), message.getX2(), message.getY2(),
                        getColorFromName(message.getColor()), message.getStrokeWidth());
                break;
            case TEXT:
                drawingPanel.drawText(message.getX1(), message.getY1(), message.getText(),
                        getColorFromName(message.getColor()));
                break;
            case ERASE:
                drawingPanel.erase(message.getX1(), message.getY1(), message.getStrokeWidth());
                break;
            case CLEAR:
                drawingPanel.clear();
                break;
            case USER_JOIN:
                statusLabel.setText("User joined: " + message.getUsername());
                break;
            case USER_LEAVE:
                statusLabel.setText("User left: " + message.getUsername());
                break;
        }
    }
    
    /**
     * Custom panel for drawing operations
     */
    private class DrawingPanel extends JPanel {
        private BufferedImage canvas;
        private Graphics2D g2d;
        private Point lastPoint;
        private boolean drawing = false;
        
        public DrawingPanel() {
            setPreferredSize(new Dimension(800, 600));
            setBackground(Color.WHITE);
            initCanvas();
            setupMouseListeners();
        }
        
        private void initCanvas() {
            canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
            g2d = canvas.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 800, 600);
        }
        
        private void setupMouseListeners() {
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastPoint = e.getPoint();
                    drawing = true;
                    
                    if (currentMode == DrawingMode.TEXT) {
                        handleTextInput(e.getX(), e.getY());
                    } else if (currentMode == DrawingMode.ERASE) {
                        erase(e.getX(), e.getY(), currentStrokeWidth * 3);
                        DrawingMessage eraseMsg = new DrawingMessage(DrawingMessage.MessageType.ERASE,
                                client.getUsername(), e.getX(), e.getY(), 0, 0,
                                getColorName(currentColor), currentStrokeWidth * 3);
                        client.sendMessage(eraseMsg);
                    }
                }
                
                @Override
                public void mouseReleased(MouseEvent e) {
                    drawing = false;
                    lastPoint = null;
                }
                
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (drawing && currentMode == DrawingMode.DRAW && lastPoint != null) {
                        Point currentPoint = e.getPoint();
                        
                        // Draw locally
                        drawLine(lastPoint.x, lastPoint.y, currentPoint.x, currentPoint.y,
                                currentColor, currentStrokeWidth);
                        
                        // Send to server
                        DrawingMessage drawMsg = new DrawingMessage(DrawingMessage.MessageType.DRAW,
                                client.getUsername(), lastPoint.x, lastPoint.y, currentPoint.x, currentPoint.y,
                                getColorName(currentColor), currentStrokeWidth);
                        client.sendMessage(drawMsg);
                        
                        lastPoint = currentPoint;
                    } else if (drawing && currentMode == DrawingMode.ERASE) {
                        erase(e.getX(), e.getY(), currentStrokeWidth * 3);
                        DrawingMessage eraseMsg = new DrawingMessage(DrawingMessage.MessageType.ERASE,
                                client.getUsername(), e.getX(), e.getY(), 0, 0,
                                getColorName(currentColor), currentStrokeWidth * 3);
                        client.sendMessage(eraseMsg);
                    }
                }
            };
            
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }
        
        private void handleTextInput(int x, int y) {
            String text = JOptionPane.showInputDialog(this, "Enter text:");
            if (text != null && !text.trim().isEmpty()) {
                drawText(x, y, text, currentColor);
                
                DrawingMessage textMsg = new DrawingMessage(DrawingMessage.MessageType.TEXT,
                        client.getUsername(), x, y, text, getColorName(currentColor));
                client.sendMessage(textMsg);
            }
        }
        
        public void drawLine(int x1, int y1, int x2, int y2, Color color, int strokeWidth) {
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawLine(x1, y1, x2, y2);
            repaint();
        }
        
        public void drawText(int x, int y, String text, Color color) {
            g2d.setColor(color);
            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            g2d.drawString(text, x, y);
            repaint();
        }
        
        public void erase(int x, int y, int size) {
            g2d.setColor(Color.WHITE);
            g2d.fillOval(x - size/2, y - size/2, size, size);
            repaint();
        }
        
        public void clear() {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (canvas != null) {
                g.drawImage(canvas, 0, 0, null);
            }
        }
    }
}
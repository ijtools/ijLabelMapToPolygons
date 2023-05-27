/**
 * 
 */
package net.ijt.labels;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.geometry.Polygon2D;

/**
 * Track the boundary of a binary or label image to return a single polygon.
 * 
 * @author dlegland
 *
 */
public class BoundaryTracker
{
    /**
     * The connectivity to use for tracking boundary. Should be either 4 or 8.
     * Default is 4.
     */
    int conn = 4;
    
    /**
     * Defines where the ROI vertices are located according to position of
     * current pixel.
     */
    VertexLocation vertexLocation = VertexLocation.EDGE_CENTER;

    enum Direction
    {
        RIGHT
        {
            @Override
            public int[][] coordsShifts()
            {
                return new int[][] {{1, 0}, {1, 1}};
            }
            
            @Override
            public Point getVertex(Position pos)
            {
                return new Point(pos.x, pos.y + 1);
            }
            
            @Override
            public Position turnLeft(Position pos)
            {
                return new Position(pos.x, pos.y, UP);
            }

            @Override
            public Position forward(Position pos)
            {
                return new Position(pos.x + 1, pos.y, RIGHT);
            }

            @Override
            public Position turnRight(Position pos)
            {
                return new Position(pos.x + 1, pos.y + 1, DOWN);
            }
        },
        
        UP
        {
            @Override
            public int[][] coordsShifts()
            {
                return new int[][] {{0, -1}, {1, -1}};
            }
            
            @Override
            public Point getVertex(Position pos)
            {
                return new Point(pos.x + 1, pos.y + 1);
            }
            
            @Override
            public Position turnLeft(Position pos)
            {
                return new Position(pos.x, pos.y, LEFT);
            }

            @Override
            public Position forward(Position pos)
            {
                return new Position(pos.x, pos.y - 1, UP);
            }

            @Override
            public Position turnRight(Position pos)
            {
                return new Position(pos.x + 1, pos.y - 1, RIGHT);
            }
        },
        
        LEFT
        {
            @Override
            public int[][] coordsShifts()
            {
                return new int[][] {{-1, 0}, {-1, -1}};
            }
            
            @Override
            public Point getVertex(Position pos)
            {
                return new Point(pos.x + 1, pos.y);
            }
            
            @Override
            public Position turnLeft(Position pos)
            {
                return new Position(pos.x, pos.y, DOWN);
            }

            @Override
            public Position forward(Position pos)
            {
                return new Position(pos.x - 1, pos.y, LEFT);
            }

            @Override
            public Position turnRight(Position pos)
            {
                return new Position(pos.x - 1, pos.y - 1, UP);
            }
        },
        
        DOWN
        {
            @Override
            public int[][] coordsShifts()
            {
                return new int[][] {{0, +1}, {-1, 1}};
            }
            
            @Override
            public Point getVertex(Position pos)
            {
                return new Point(pos.x, pos.y);
            }
            
            @Override
            public Position turnLeft(Position pos)
            {
                return new Position(pos.x, pos.y, RIGHT);
            }

            @Override
            public Position forward(Position pos)
            {
                return new Position(pos.x, pos.y + 1, DOWN);
            }

            @Override
            public Position turnRight(Position pos)
            {
                return new Position(pos.x - 1, pos.y + 1, LEFT);
            }
        };

        /**
         * Returns a 2-by-2 array corresponding to a pair of coordinates shifts,
         * that will be used to access coordinates of next pixels within
         * configuration.
         * 
         * The first coordinates will be the pixel in the continuation of the
         * current direction. The second coordinate will be the pixel in the
         * opposite current 2-by-2 configuration.
         * 
         * @return a 2-by-2 array corresponding to a pair of coordinates shifts.
         */
        public abstract int[][] coordsShifts();
        
        public abstract Point getVertex(Position pos);
        
        /**
         * Keeps current reference pixel and turn direction by +90 degrees in
         * counter-clockwise direction.
         * 
         * @param pos
         *            the position to update
         * @return the new position
         */
        public abstract Position turnLeft(Position pos);

        /**
         * Updates the specified position by iterating by one step in the
         * current direction.
         * 
         * @param pos
         *            the position to update
         * @return the new position
         */
        public abstract Position forward(Position pos);
        
        /**
         * Keeps current reference pixel and turn direction by -90 degrees in
         * counter-clockwise direction.
         * 
         * @param pos
         *            the position to update
         * @return the new position
         */
        public abstract Position turnRight(Position pos);
    }
    
    
    static final class Position
    {
        int x;
        int y;
        Direction direction;
        
        Position(int x, int y, Direction direction)
        {
            this.x = x;
            this.y = y;
            this.direction = direction;
        }
        
        public Point getVertex(Position pos)
        {
            return this.direction.getVertex(this);
        }
        
        public Point2D getVertex(Position pos, VertexLocation vertex)
        {
            switch (vertex)
            {
                case CORNER: 
                    return this.direction.getVertex(this);
                case EDGE_CENTER: 
                    switch(direction)
                    {
                        case DOWN: return new Point2D.Double(this.x, this.y + 0.5);
                        case UP:   return new Point2D.Double(this.x + 1.0, this.y + 0.5);
                        case LEFT: return new Point2D.Double(this.x + 0.5, this.y);
                        case RIGHT: return new Point2D.Double(this.x + 0.5, this.y + 1.0);
                    }
                case PIXEL: 
                    return new Point2D.Double(this.x + 0.5, this.y + 0.5);
                default: throw new IllegalArgumentException("Unexpected value: " + vertex);
            }
        }
        
        @Override
        public boolean equals(Object obj)
        {
            // check class
            if (!(obj instanceof Position))
                return false;
            Position that = (Position) obj;
            
            // check each class member
            if (this.x != that.x)
                return false;
            if (this.y != that.y)
                return false;
            if (this.direction != that.direction)
                return false;
            
            // return true when all tests checked
            return true;
        }
    }
    
    enum VertexLocation
    {
        CORNER,
        EDGE_CENTER,
        PIXEL;
    }
    
    /**
     * Default empty constructor, using Connectivity 4.
     */
    public BoundaryTracker()
    {
    }
    
    /**
     * Constructor that allows to specify connectivity.
     * 
     * @param conn
     *            the connectivity to use (must be either 4 or 8)
     */
    public BoundaryTracker(int conn)
    {
        if (conn != 4 && conn != 8)
        {
            throw new IllegalArgumentException(
                    "Connectivity must be either 4 or 8");
        }
        this.conn = conn;
    }
    
    /**
     * Constructor that allows to specify connectivity and location of vertices.
     * 
     * @param conn
     *            the connectivity to use (must be either 4 or 8)
     */
    public BoundaryTracker(int conn, VertexLocation loc)
    {
        if (conn != 4 && conn != 8)
        {
            throw new IllegalArgumentException(
                    "Connectivity must be either 4 or 8");
        }
        this.conn = conn;
        this.vertexLocation = loc;
    }
    
    /**
     * Tracks the boundary that starts at the current position by iterating on
     * successive neighbor positions, and returns the set of boundary points.
     * 
     * The positions are defined by two coordinates and a direction. The initial
     * position must correspond to a transition into a region, and the resulting
     * boundary will surround this region.
     * 
     * @param array
     *            the array containing binary or label representing the
     *            region(s)
     * @param x0
     *            the x-coordinate of the start position
     * @param y0
     *            the y-coordinate of the start position
     * @param initialDirection
     *            the direction of the start position
     * @return the list of points that form the boundary starting at specified
     *         position
     */
    public ArrayList<Point2D> trackBoundary(ImageProcessor array, int x0,
            int y0, Direction initialDirection)
    {
        // retrieve image size
        int sizeX = array.getWidth();
        int sizeY = array.getHeight();
        
        // initialize result array
        ArrayList<Point2D> vertices = new ArrayList<Point2D>();
        
        // initialize tracking algo state
        int value = (int) array.getf(x0, y0);
        Position pos0 = new Position(x0, y0, initialDirection);
        Position pos = new Position(x0, y0, initialDirection);
        
        // iterate over boundary until we come back at initial position
        do
        {
            vertices.add(pos.getVertex(pos, vertexLocation));
            
            // compute position of the two other points in current 2-by-2 configuration
            int[][] shifts = pos.direction.coordsShifts();
            // the pixel in the continuation of current direction
            int xn = pos.x + shifts[0][0];
            int yn = pos.y + shifts[0][1];
            // the pixel in the diagonal position within current configuration
            int xd = pos.x + shifts[1][0];
            int yd = pos.y + shifts[1][1];
            
            // determine configuration of the two pixels in current direction
            // initialize with false, to manage the case of configuration on the
            // border. In any cases, assume that reference pixel in current
            // position belongs to the array.
            boolean b0 = false;
            if (xn >= 0 && xn < sizeX && yn >= 0 && yn < sizeY)
            {
                b0 = ((int) array.getf(xn, yn)) == value;
            }
            boolean b1 = false;
            if (xd >= 0 && xd < sizeX && yd >= 0 && yd < sizeY)
            {
                b1 = ((int) array.getf(xd, yd)) == value;
            }
            
            // Depending on the values of the two other pixels in configuration,
            // update the current position
            if (!b0 && (!b1 || conn == 4))
            {
                // corner configuration -> +90 direction
                pos = pos.direction.turnLeft(pos);
            } 
            else if (b1 && (b0 || conn == 8))
            {
                // reentrant corner configuration -> -90 direction
                pos = pos.direction.turnRight(pos);
            } 
            else if (b0 && !b1)
            {
                // straight border configuration -> same direction
                pos = pos.direction.forward(pos);
            } 
            else
            {
                throw new RuntimeException("Should not reach this part...");
            }
        } while (!pos0.equals(pos));
        
        return vertices;
    }
    
    public Map<Integer, ArrayList<Polygon2D>> process(ImageProcessor array)
    {
        // retrieve image size
        int sizeX = array.getWidth();
        int sizeY = array.getHeight();
        
        ByteProcessor maskArray = new ByteProcessor(sizeX, sizeY);
        
        Map<Integer, ArrayList<Polygon2D>> boundaries = new HashMap<>();
        
        // iterate over all image pixels
        for (int y = 0; y < sizeY; y++)
        {
            int currentLabel = 0;
            
            for (int x = 0; x < sizeX; x++)
            {
                int label = (int) array.getf(x, y);

                // first check if this is a transition between two labels
                if (label == currentLabel)
                {
                    continue;
                }
                currentLabel = label;
                
                // do not process background values
                if (label == 0)
                {
                    continue;
                }
                // if the boundary was already tracked, no need to work again
                if ((maskArray.get(x, y) & 0x08) > 0)
                {
                    continue;
                }
                
                // ok, we are at a transition that can be used to initialize a new boundary
                // -> track the boundary, and convert to polygon object
                ArrayList<Point2D> vertices = trackBoundary(array, maskArray, x, y, Direction.DOWN);
                Polygon2D poly = createPolygon(vertices);
                
                // update map from labels to array of polygons
                ArrayList<Polygon2D> polygons = boundaries.get(label);
                if (polygons == null)
                {
                    polygons = new ArrayList<Polygon2D>(4);
                }
                polygons.add(poly);
                boundaries.put(label, polygons);
            }
        }
        
        return boundaries;
    }
    
    /**
     * Tracks the boundary that starts at the current position by iterating on
     * successive neighbor positions, and returns the set of boundary points.
     * 
     * The positions are defined by two coordinates and a direction. The initial
     * position must correspond to a transition into a region, and the resulting
     * boundary will surround this region.
     * 
     * @param array
     *            the array containing binary or label representing the
     *            region(s)
     * @param maskArray
     *            an array the same size as <code>array</code> containing a
     *            4-bits values that indicates which directions of current pixel
     *            have been visited.
     * @param x0
     *            the x-coordinate of the start position
     * @param y0
     *            the y-coordinate of the start position
     * @param initialDirection
     *            the direction of the start position
     * @return the list of points that form the boundary starting at specified
     *         position
     */
    private ArrayList<Point2D> trackBoundary(ImageProcessor array, ImageProcessor maskArray, int x0,
            int y0, Direction initialDirection)
    {
        // retrieve image size
        int sizeX = array.getWidth();
        int sizeY = array.getHeight();
        
        // initialize result array
        ArrayList<Point2D> vertices = new ArrayList<Point2D>();
        
        // initialize tracking algo state
        int value = (int) array.getf(x0, y0);
        Position pos0 = new Position(x0, y0, initialDirection);
        Position pos = new Position(x0, y0, initialDirection);
        
        // iterate over boundary until we come back at initial position
        do
        {
            // update vertices
            vertices.add(pos.getVertex(pos, vertexLocation));
            
            // mark the current pixel with integer that depends on position
            int mask = maskArray.get(pos.x, pos.y);
            switch (pos.direction)
            {
            case RIGHT: mask = mask | 0x01; break;
            case UP:    mask = mask | 0x02; break;
            case LEFT:  mask = mask | 0x04; break;
            case DOWN:  mask = mask | 0x08; break;
            }
            maskArray.set(pos.x, pos.y, mask);
            
            // compute position of the two other points in current 2-by-2 configuration
            int[][] shifts = pos.direction.coordsShifts();
            // the pixel in the continuation of current direction
            int xn = pos.x + shifts[0][0];
            int yn = pos.y + shifts[0][1];
            // the pixel in the diagonal position within current configuration
            int xd = pos.x + shifts[1][0];
            int yd = pos.y + shifts[1][1];
            
            // determine configuration of the two pixels in current direction
            // initialize with false, to manage the case of configuration on the
            // border. In any cases, assume that reference pixel in current
            // position belongs to the array.
            boolean b0 = false;
            if (xn >= 0 && xn < sizeX && yn >= 0 && yn < sizeY)
            {
                b0 = ((int) array.getf(xn, yn)) == value;
            }
            boolean b1 = false;
            if (xd >= 0 && xd < sizeX && yd >= 0 && yd < sizeY)
            {
                b1 = ((int) array.getf(xd, yd)) == value;
            }
            
            // Depending on the values of the two other pixels in configuration,
            // update the current position
            if (!b0 && (!b1 || conn == 4))
            {
                // corner configuration -> +90 direction
                pos = pos.direction.turnLeft(pos);
            } 
            else if (b1 && (b0 || conn == 8))
            {
                // reentrant corner configuration -> -90 direction
                pos = pos.direction.turnRight(pos);
            } 
            else if (b0 && !b1)
            {
                // straight border configuration -> same direction
                pos = pos.direction.forward(pos);
            } 
            else
            {
                throw new RuntimeException("Should not reach this part...");
            }
        } while (!pos0.equals(pos));
        
        return vertices;
    }
    
    private static final Polygon2D createPolygon(ArrayList<Point2D> vertices)
    {
        int n = vertices.size();
        double[] vx = new double[n];
        double[] vy = new double[n];
        for (int i = 0; i < n; i++)
        {
            Point2D p = vertices.get(i);
            vx[i] = p.getX();
            vy[i] = p.getY();
        }
        return new Polygon2D(vx, vy);
    }
}

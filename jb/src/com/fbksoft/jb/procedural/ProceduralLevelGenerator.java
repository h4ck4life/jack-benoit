package com.fbksoft.jb.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.math.GridPoint2;
import com.fbksoft.engine.tiles.CommonTile;
import com.fbksoft.engine.tiles.JBLevelLayout;
import com.fbksoft.engine.tiles.Room;
import com.fbksoft.engine.tiles.RoomType;
import com.fbksoft.engine.tiles.WorldTile;
import com.fbksoft.engine.tiles.JBLevelLayout.Direction;
import com.fbksoft.jb.Constants;
import com.fbksoft.jb.Level;

public class ProceduralLevelGenerator {
	
	private TiledMapTileLayer platformLayer;
	private TiledMapTileLayer ladderLayer;
	private TiledMapTileLayer spriteLayer;
	private TiledMapTileLayer backgroundLayer;
	
	private TiledMapTileSet commonTileSet;
	private TiledMapTileSet worldTileSet;
	
	private int roomHeight;
	private int roomWidth;
	private Random rng;
	/** for each room, the relative altitude */
	private int[][] groundAltitudes;

	private List<Platform>[] generatedPlatforms;
	private List<Ladder> ladderList;
	private int worldId;
	
	GridPoint2 startPosition = null;
	GridPoint2 endPosition = null;
	
	public ProceduralLevelGenerator(TiledMap tiledMap, int roomHeight, int roomWidth, int worldId) {
		super();		 
		this.worldId = worldId;
		this.rng = new Random();
		this.platformLayer = (TiledMapTileLayer) tiledMap.getLayers().get(Constants.PLATFORM_LAYER);
		this.ladderLayer = (TiledMapTileLayer) tiledMap.getLayers().get(Constants.LADDER_LAYER);
		this.spriteLayer = (TiledMapTileLayer) tiledMap.getLayers().get(Constants.SPRITE_LAYER);
		this.backgroundLayer = (TiledMapTileLayer) tiledMap.getLayers().get(Constants.PARRALAX_LAYER);
		this.commonTileSet = tiledMap.getTileSets().getTileSet(0);
		this.worldTileSet = tiledMap.getTileSets().getTileSet(1);
		this.roomHeight = roomHeight;
		this.roomWidth = roomWidth;

		System.out.println(String.format("layer width: %d, height: %d", platformLayer.getWidth(), platformLayer.getHeight()));
		int horizontalRooms = (platformLayer.getWidth()) / roomWidth;
		int verticalRooms = (platformLayer.getHeight()) / roomHeight;
		System.out.println(String.format("Number of horizontal rooms: %d, vertical: %d", horizontalRooms, verticalRooms));

		this.groundAltitudes = new int[horizontalRooms * verticalRooms][roomWidth];
		this.generatedPlatforms = new ArrayList[horizontalRooms * verticalRooms];
		this.ladderList = new ArrayList<Ladder>();
	}

	private void fillRoom(Room room) {
		for (int y = 0; y < roomHeight; y++) {
			for (int x = 0; x < roomWidth; x++) {
				setPlatformTile(room.offsetX, room.offsetY, x, y);
			}
		}
	}

	public void decorateRoom(Room room) {
//		System.out.println("Create room " + room);

		if (room.ground) {
			generateGround(room.id, room.offsetX, room.offsetY);
		}

		createRoomWalls(room);
		
		if (room.roomType != RoomType.FILLED) {			
			createRandomPlatforms(room.id, room.offsetX, room.offsetY, room.ground);
			createRandomLadders(room.id, room.offsetX, room.offsetY);
		}
		
		createEnvironmentalHazard(room);
		createSprites(room.id);

	}

	private void createEnvironmentalHazard(Room room) {
		for (int y = 0; y < roomHeight - 1; y++) {
			for (int x = 1; x < roomWidth - 1; x++) {
				if (blockAt(room.offsetX, room.offsetY, x, y)) {
					if (!blockAt(room.offsetX, room.offsetY, x, y+1) && !ladderAt(room.offsetX, room.offsetY, x, y)) {
						if (rng.nextFloat() > 0.95) {
							ladderLayer.setCell(room.offsetX+x, room.offsetY+y+1, WorldTile.SPIKE.toCell(worldTileSet));
						}
					}
				}
			}
		}		
	}

	public void setOnLadderLayerIfEmpty(WorldTile tile, int x, int y) {
		if (ladderLayer.getCell(x, y) == null) {
			ladderLayer.setCell(x, y, tile.toCell(worldTileSet));
		}
	}
		
	
	private void createSprites(int roomIndex) {
		List<Platform> platformList = generatedPlatforms[roomIndex];		
		if (platformList == null) {
			return;
		}
		int zombies = rng.nextInt(5);
		for (int i = 0; i < zombies; i++) {
			// get a random platform
			
			Platform platform = platformList.get(rng.nextInt(platformList.size()));
			int randomPlacement = rng.nextInt(platform.length);
			
			if (!blockAt(platform.x+randomPlacement, platform.y+1)) {				
				int random = rng.nextInt(3);
				if (random == 0) {
					spriteLayer.setCell(platform.x+randomPlacement, platform.y+1, CommonTile.FLOWER.toCell(commonTileSet));
				}
				else if (random == 1) {
					ladderLayer.setCell(platform.x+randomPlacement, platform.y, CommonTile.SPIDER.toCell(commonTileSet));
					spriteLayer.setCell(platform.x+randomPlacement, platform.y, CommonTile.SPIDER.toCell(commonTileSet));
				}
				else {
					if (worldId % Level.NUMBER_OF_WORLDS == 1 || worldId % Level.NUMBER_OF_WORLDS == 3) {
						spriteLayer.setCell(platform.x+randomPlacement, platform.y+1, CommonTile.ZOMBIE.toCell(commonTileSet));
					}
					else if (worldId % Level.NUMBER_OF_WORLDS == 2) {
						spriteLayer.setCell(platform.x+randomPlacement, platform.y+1, CommonTile.ESKIMO.toCell(commonTileSet));
					}
				}
			}
		}
		
		// Collectables
		for(Platform platform : platformList) {
			if (rng.nextDouble() > 0.5d) {
				for (int jewelIndex = 0; jewelIndex < platform.length; jewelIndex++) {
					if (!blockAt(platform.x+jewelIndex, platform.y+1) && rng.nextDouble() > 0.6) {
						spriteLayer.setCell(platform.x+jewelIndex, platform.y+1, CommonTile.BLUE_JEWEL.toCell(commonTileSet));
					}
				}
			}
		}
	}

	private void createRoomWalls(Room room) {

		for (int y = roomHeight - 1; y >= 0; y--) {
			if (room.leftWall) {
				setPlatformTile(room.offsetX, room.offsetY, 0, y);
			}
			if (room.rightWall) {
				setPlatformTile(room.offsetX, room.offsetY, roomWidth - 1, y);
			}
		}

		if (room.bottomWall) {
			for (int x = 0; x < roomWidth; x++) {
				platformLayer.setCell(room.offsetX + x, room.offsetY, WorldTile.DIRT.toCell(worldTileSet));
			}
		}

		if (room.topWall) {
			for (int x = 0; x < roomWidth; x++) {
				platformLayer.setCell(room.offsetX + x, room.offsetY + roomHeight - 1, WorldTile.DIRT.toCell(worldTileSet));
			}
		}
	}

	private void generateGround(int roomIndex, int roomOffsetX, int roomOffsetY) {
		int groundAltitude = 0;
		boolean hasHole = false;
		int currentHoleWidth = 0;
		// Generate random terrain
		for (int x = 0; x < roomWidth; x++) {
			int noise1 = -rng.nextInt(4) + 2;

			groundAltitude += noise1;
			if (groundAltitude > 3) {
				groundAltitude = 3;
			}
			if (groundAltitude < 0) {
				groundAltitude = 0;
			}

			// Max hole size == 4
			else if (currentHoleWidth >= 4 && groundAltitude == 0) {
				groundAltitude = 1;
				currentHoleWidth = 0;
			}

			groundAltitudes[roomIndex][x] = groundAltitude;

			if (groundAltitude == 0) { // current hole
				hasHole = true;
				currentHoleWidth++;
			} else if (currentHoleWidth > 0) { // end of hole
				currentHoleWidth = 0;
			}

			if (groundAltitude > 0) {
				// Fill Ground
				for (int i = 0; i < groundAltitude; i++) {
					setPlatformTile(roomOffsetX, roomOffsetY, x, i);
				}

				int groundX = roomOffsetX + x;
				int groundY = roomOffsetY + groundAltitudes[roomIndex][x];
				platformLayer.setCell(groundX, groundY, WorldTile.GROUND.toCell(worldTileSet));
			}
		}

		if (!hasHole) { // Create at least one hole
			int randomIndex = 1 + (int) Math.random() * (roomWidth - 2);
			groundAltitudes[roomIndex][randomIndex] = 0;
		}

	}

	private void createRandomPlatforms(int roomIndex, int roomOffsetX, int roomOffsetY, boolean ground) {

		generatedPlatforms[roomIndex] = new ArrayList<Platform>();

		for (int y = roomHeight - 3; y > 0; y--) {
			if (Math.random() <= 0.7) {
				for (int x = 1; x < roomWidth - 2; x++) {
					double nextGaussian = rng.nextGaussian() + 1;
					if (nextGaussian < 0) {
						nextGaussian = 0;
					}
					if (nextGaussian > 2) {
						nextGaussian = 2;
					}

					if (Math.random() < 0.2) {
						int platformLength = (int) (nextGaussian * 4);
						if (x + platformLength > roomWidth) {
							platformLength = roomWidth - x;
						}
						int i = 0;
						if (platformLength > 0) {
							for (i = 0; i < platformLength; i++) {
								if (!blockAt(roomOffsetX, roomOffsetY, x + i, y - 2) && !blockAt(roomOffsetX, roomOffsetY, x + i, y - 1)) {
									setPlatformTile(roomOffsetX, roomOffsetY, x + i, y);
								}
								else {
									break;
								}
							}

							if (i > 0) {
								generatedPlatforms[roomIndex].add(new Platform(roomOffsetX + x, roomOffsetY + y, i));
							}
						}

						int variableSpaceLength = 1 + (int) rng.nextDouble() * 8;
						x += i + variableSpaceLength;
					}
				}
			}
		}
	}

	private void createRandomLadders(int roomIndex, int roomOffsetX, int roomOffsetY) {

		List<Platform> platformList = generatedPlatforms[roomIndex];
		for (Platform platform : platformList) {

			int ladderX = rng.nextInt(platform.length);
			if (!blockAt(platform.x + ladderX, platform.y + 1) && !blockAt(platform.x + ladderX, platform.y - 1)) {
				setLadderTile(platform.x + ladderX, platform.y, true);
				ladderList.add(new Ladder(platform.x + ladderX, platform.y, 1));
			}

		}
	}

	@SuppressWarnings("unused")
	private void dumpRoom(int roomIndex, int roomOffsetX, int roomOffsetY) {
		for (int y = roomHeight - 1; y >= 0; y--) {
			System.out.print(String.format("%02d", y));
			for (int x = 0; x < roomWidth; x++) {
				if (blockAt(roomOffsetX, roomOffsetY, x, y)) {
					System.out.print("@");
				} else {
					System.out.print(" ");
				}
			}
			System.out.println();
		}
		System.out.print("  ");
		for (int x = 0; x < roomWidth; x++) {
			System.out.print(groundAltitudes[roomIndex][x]);
		}
		System.out.println();
	}

	private boolean ladderAt(int roomOffsetX, int roomOffsetY, int relativeX, int relativeY) {
		int x = roomOffsetX + relativeX;
		int y = roomOffsetY + relativeY;
		return ladderLayer.getCell(x, y) != null && ladderLayer.getCell(x, y).getTile().getProperties().containsKey("ladder");
	}

	private boolean blockAt(int roomOffsetX, int roomOffsetY, int relativeX, int relativeY) {
		int x = roomOffsetX + relativeX;
		int y = roomOffsetY + relativeY;
		return blockAt(x, y);
	}

	private boolean blockAt(int x, int y) {
		return platformLayer.getCell(x, y) != null;
	}

	private void setLadderTile(int x, int y, boolean top) {
		if (x >= 0 && x < platformLayer.getWidth() && y >= 0 && y < platformLayer.getHeight()) {
			ladderLayer.setCell(x, y, top ? WorldTile.LADDER_TOP.toCell(worldTileSet) : WorldTile.LADDER.toCell(worldTileSet));
		}
	}

	private void clearLadderTile(int x, int y) {
		if (x >= 0 && x < platformLayer.getWidth() && y >= 0 && y < platformLayer.getHeight()) {
			ladderLayer.setCell(x, y, null);
		}
	}

	private void setPlatformTile(int roomOffsetX, int roomOffsetY, int relativeX, int relativeY) {
		int x = roomOffsetX + relativeX;
		int y = roomOffsetY + relativeY;
		setPlatformTile(x, y, WorldTile.DIRT);
	}

	private void setPlatformTile(int x, int y, WorldTile tile) {
		if (x >= 0 && x < platformLayer.getWidth() && y >= 0 && y < platformLayer.getHeight()) {
			platformLayer.setCell(x, y, tile.toCell(worldTileSet));
		} else {
			throw new RuntimeException("setPlatform out of bounds" + x + "," + y + " => " + platformLayer.getWidth() + "," + platformLayer.getHeight());
		}
	}

	/*private void clearTiles(int roomOffsetX, int roomOffsetY, int relativeX, int relativeY) {
		int x = roomOffsetX + relativeX;
		int y = roomOffsetY + relativeY;
		platformLayer.setCell(x, y, null);
		ladderLayer.setCell(x, y, null);
	}*/

	public void tilingPostProcessing() {
		// Create platform tiles in the layer
		for (int y = platformLayer.getHeight() - 2; y > 0; y--) {
			for (int x = 0; x < platformLayer.getWidth(); x++) {
				if (blockAt(x, y) && !blockAt(x, y - 1) && !blockAt(x, y + 1)) {
					boolean blockLeft = blockAt(x - 1, y);
					boolean blockRight = blockAt(x + 1, y);

					if ((blockLeft && blockRight) || (!blockLeft && !blockRight)) {
						setPlatformTile(x, y, WorldTile.PLATFORM);
					} else if (blockLeft) {
						setPlatformTile(x, y, WorldTile.PLATFORM_RIGHT);
					} else if (blockRight) {
						setPlatformTile(x, y, WorldTile.PLATFORM_LEFT);
					}
				}
			}
		}

		for (int y = platformLayer.getHeight() - 1; y >= 0; y--) {
			for (int x = 0; x < platformLayer.getWidth(); x++) {
				if (blockAt(x, y) && blockAt(x, y - 1) && !blockAt(x, y + 1)) {
					platformLayer.setCell(x, y, WorldTile.GROUND.toCell(worldTileSet));
				}
			}
		}
		for (int y = platformLayer.getHeight() - 1; y >= 0; y--) {
			for (int x = 0; x < platformLayer.getWidth(); x++) {
				if (blockAt(x, y) && blockAt(x, y + 1) && !blockAt(x, y - 1)) {
					platformLayer.setCell(x, y, WorldTile.HALF_DIRT.toCell(worldTileSet));
				}
			}
		}

		// Grow ladders
		for (Ladder ladder : ladderList) {
			int ladderX = ladder.x, ladderY = ladder.y - 1;
			boolean groundFound = false;
			while (!groundFound && ladderY > 1) {
				Cell cell = platformLayer.getCell(ladderX, ladderY - 1);
				if (cell != null) {
					groundFound = true;
				} else {
					setLadderTile(ladderX, ladderY, false);
				}
				ladderY--;
			}
		}

	}

	public static TiledMap generateMap(TiledMap master, JBLevelLayout levelLayout, int worldId) {		
		TiledMap map = cloneMapWithLayout(master, levelLayout, worldId);

		ProceduralLevelGenerator proceduralArtGenerator = new ProceduralLevelGenerator(map, levelLayout.roomHeightInTiles, levelLayout.roomWidthInTiles, worldId);
		
		System.out.println("Fill background");
		proceduralArtGenerator.fillBackground();
		
		System.out.println("Build path");
		proceduralArtGenerator.buildLevelPath(levelLayout);
		
		
		for (Room room : levelLayout) {
			proceduralArtGenerator.decorateRoom(room);
		}
		
		for (Room room : levelLayout.filledRoomsList) {
			proceduralArtGenerator.fillRoom(room);
		}
				
		proceduralArtGenerator.tilingPostProcessing();
		
		//new TmxExporter(map).export(new File("data/output/generated.tmx"));

		return map;
	}

	private void buildLevelPath(JBLevelLayout levelLayout) {		
		GridPoint2 wpStartPosition = null;
		GridPoint2 wpTargetPosition = null;
		
		for (Room room : levelLayout) {
			if (wpStartPosition == null) {
				wpStartPosition = levelLayout.randomPositionInRoom(room, rng);
				this.startPosition = wpStartPosition;
			}			 
			
			Room nextRoom = levelLayout.nextRoom(room);
			if (nextRoom == null) {
				continue;
			}
			wpTargetPosition = levelLayout.randomPositionInRoom(nextRoom, rng);
			
				buildLevelPathBetween(wpStartPosition, wpTargetPosition, room.orientation.current);
			wpStartPosition = wpTargetPosition;
		}
		this.endPosition = wpTargetPosition;

		System.out.println("Jack tile position at " + startPosition.x + ", " + startPosition.y);
		spriteLayer.setCell(startPosition.x, startPosition.y, CommonTile.JACK.toCell(commonTileSet));
		
		ladderLayer.setCell(endPosition.x, endPosition.y, CommonTile.EXIT.toCell(commonTileSet));
		platformLayer.setCell(endPosition.x, endPosition.y, null);
		platformLayer.setCell(endPosition.x, endPosition.y - 1, WorldTile.DIRT.toCell(worldTileSet));
	}

	private void buildLevelPathBetween(GridPoint2 startPosition, GridPoint2 targetPosition, Direction direction) {
		
		GridPoint2 cursor = new GridPoint2(startPosition);
		int dx = targetPosition.x-cursor.x;
		int dy = targetPosition.y-cursor.y;
		
		int randomDx = -1; // Random x position to place a roadsign on this path section -1 if not applicable (vertical movement)
		if (direction == Direction.EAST || direction == Direction.WEST) {
			randomDx = cursor.x + (int)(rng.nextFloat()*dx);
		}
					
		int last = -1; // 1 platform 2 ladder up 3 ladder down
		
		while(cursor.x != targetPosition.x || cursor.y != targetPosition.y) {
			
			dx = targetPosition.x-cursor.x;
			dy = targetPosition.y-cursor.y;
			
			if (Math.abs(dx) > Math.abs(dy)) {
				int sign = (int)Math.signum(dx);
				
				// Create a random platform									
				
				if (last != 1) {
					clearLadderTile(cursor.x, cursor.y);
				}
				
				for (int pi = 0; pi < Math.abs(dx) + 1; pi++) {						
					int platformX = cursor.x+(pi*sign);
					int platformY = cursor.y-1;
					setPlatformTile(platformX, platformY, WorldTile.DIRT);	
					if (platformX == randomDx) {
						setOnLadderLayerIfEmpty(sign > 0 ? WorldTile.ROAD_SIGN_RIGHT : WorldTile.ROAD_SIGN_LEFT, platformX, platformY+1);
					}
				}										
				cursor.x+=dx;
				
				last = 1;
			}
			else {
				int sign = (int)Math.signum(dy);
				
				// Create a random ladder								
				if (dy<=2 && dy > 0) {					
					if (last != 1) {
						clearLadderTile(cursor.x, cursor.y);
					}
					for (int pi = 0; pi <= Math.abs(dy)-1; pi++) {						
						setPlatformTile(cursor.x, cursor.y+(pi*sign), WorldTile.DIRT);							
					}
				}
				else {
					int startLadderIndex = (last == 1 ? 1 : 0);
					
					for (int pi = startLadderIndex; pi <= Math.abs(dy); pi++) {					
						setLadderTile(cursor.x, cursor.y+(pi*sign), false);
					}						
				}
				cursor.y+=dy;
				
				last = sign > 0 ? 2 : 3;
			}
		}
	}
	
	private static TiledMap cloneMapWithLayout(TiledMap master, JBLevelLayout levelLayout, int worldId) {
		return cloneMap(master, levelLayout.width, levelLayout.height, worldId);
	}

	private static TiledMap cloneMap(TiledMap master, int width, int height, int worldId) {
		TiledMap map = new TiledMap();
		TiledMapTileLayer parrallaxLayer = new TiledMapTileLayer(width, height, 32, 32);
		TiledMapTileLayer platformLayer = new TiledMapTileLayer(width, height, 32, 32);
		TiledMapTileLayer ladderLayer = new TiledMapTileLayer(width, height, 32, 32);
		TiledMapTileLayer spriteLayer = new TiledMapTileLayer(width, height, 32, 32);		
		map.getLayers().add(parrallaxLayer);
		map.getLayers().add(platformLayer);
		map.getLayers().add(ladderLayer);
		map.getLayers().add(spriteLayer);
		TiledMapTileSet commonTileSet = master.getTileSets().getTileSet(0);		
		TiledMapTileSet worldTileSet = master.getTileSets().getTileSet(worldId % Level.NUMBER_OF_WORLDS);		
		
		System.out.println("* common tileset");
		for (TiledMapTile tiledMapTile : commonTileSet) {
			Object tileId = tiledMapTile.getProperties().get("id");
			if (tileId!=null)
				System.out.println(String.format("%s(%d),", tileId, tiledMapTile.getId()));
		}
		for (TiledMapTile tiledMapTile : worldTileSet) {
			Object tileId = tiledMapTile.getProperties().get("id");
			if (tileId!=null)
				System.out.println(String.format("%s(%d),", tileId, tiledMapTile.getId()));
		}
		
		map.getTileSets().addTileSet(commonTileSet);
		map.getTileSets().addTileSet(worldTileSet);
		map.getProperties().putAll(master.getProperties());
		return map;
	}

	private void fillBackground() {
		for (int x = 0; x < backgroundLayer.getWidth(); x++) {
			for (int y = 0; y <= backgroundLayer.getHeight(); y++) {
				backgroundLayer.setCell(x, y, randomTile());				
			}
		}
		int maxSize = backgroundLayer.getHeight() > backgroundLayer.getWidth() ? backgroundLayer.getHeight() : backgroundLayer.getWidth();		
		Amortized2DNoise noise = new Amortized2DNoise(maxSize);
		noise.generate2DNoise(backgroundLayer, worldTileSet, WorldTile.BACK_LIGHT1, WorldTile.BACK_LIGHT2, 5, 5, 0, 0);
		
		// Antialias tiles
		for (int x = 1; x < backgroundLayer.getWidth() - 1; x++) {
			for (int y = 1; y < backgroundLayer.getHeight() - 1; y++) {
				Cell cell = backgroundLayer.getCell(x, y);
				
				Cell top = backgroundLayer.getCell(x, y+1);
				Cell bottom = backgroundLayer.getCell(x, y-1);
				Cell left = backgroundLayer.getCell(x-1, y);
				Cell right = backgroundLayer.getCell(x+1, y);
				
				if (!isLight(cell)) {
					if (isLight(top) && isLight(left) && !isLight(right) && !isLight(bottom)) {
						cell.setTile(WorldTile.BACK_NORTH_WEST.fromTileSet(worldTileSet));
					}
					if (isLight(top) && isLight(right) && !isLight(left) && !isLight(bottom)) {
						cell.setTile(WorldTile.BACK_NORTH_EAST.fromTileSet(worldTileSet));
					}
					if (isLight(bottom) && isLight(left) && !isLight(right) && !isLight(top)) {
						cell.setTile(WorldTile.BACK_SOUTH_WEST.fromTileSet(worldTileSet));
					}
					if (isLight(bottom) && isLight(right) && !isLight(left) && !isLight(top)) {
						cell.setTile(WorldTile.BACK_SOUTH_EAST.fromTileSet(worldTileSet));
					}
				}
			}
		}
	}

	private Cell randomTile() {
		return rng.nextBoolean() ? WorldTile.BACK_DARK1.toCell(worldTileSet) : WorldTile.BACK_DARK2.toCell(worldTileSet);
	}

	private boolean isLight(Cell cell) {		
		String name = cell.getTile().getProperties().get("id").toString();
		return name.equals(WorldTile.BACK_LIGHT1.name()) || name.equals(WorldTile.BACK_LIGHT2.name());
	}

	
}
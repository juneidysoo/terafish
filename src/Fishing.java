import java.awt.Rectangle;

import java.awt.event.KeyEvent;

import java.io.IOException;

public class Fishing{
	private static final int GAUGE_WIDTH = 420;
	private static final Rectangle GAUGE = new Rectangle(714, 309, GAUGE_WIDTH, 30);
	private static final Rectangle F = new Rectangle(957, 262, 12, 16);
	private static final Rectangle GAUGE_FRAME = new Rectangle(702, 307, 10, 50);

	private static final int BASE_WAIT_TIME = 20000; //20 secs
	private static final int WAIT_CHECK_INTERVAL = 1000; //1 sec
	private static final int TIME_TO_FISH_BUFFER = 2000; //2 secs
	private static final int SOONEST_POSSIBLE_FISH_TIME = 7000; //7 secs
	private static final int MAX_FISHING_TIME = 30000; //30 secs
	private static final int AFTER_FISHING_DELAY = 3500; //3.5 secs

	private static final double F_RATIO = 0.2;
	private static final double FRAME_RATIO = 0.01;

	private static final int MIN_GOLDEN_WHITENESS = 80000;

	private static int[][] fTpl;
	private static int[] gaugeFrameTpl;

	private enum State {
		START,
		WAIT,
		START_FISH,
		FISH,
		FINISHED
	}

	private static State state = State.START;
	private static boolean needCheckGolden = true;
	private static boolean golden = false;
	static{
		try{
			fTpl = Image.loadGreyTemplate("./f.tpl");
			gaugeFrameTpl = Image.loadRgbTemplate("./gaugeframe.tpl");
		}catch(IOException ex){
			System.out.println("Error loading template " + ex);
			System.exit(1);
		}
	}

	//Pressing state for efficiency?
	private static boolean pressing = false;

	//Time when fishing started, used to determine when to stop
	private static long fishingStarted;

	public static void fish()throws IOException, InterruptedException{
		needCheckGolden = true;
		golden = false;
		state = State.START;
		while(state!=State.FINISHED){
			//long startTime = System.currentTimeMillis();
			switch(state){
				case START:
					TeraFish.pressKey(KeyEvent.VK_R, 3000);
					state = State.WAIT;
					break;
				case WAIT:
					Thread.sleep(BASE_WAIT_TIME);
					state = State.START_FISH;
					break;
				case START_FISH:
					if(isTimeToFish()){
						TeraFish.pressKey(KeyEvent.VK_F);
						fishingStarted = System.currentTimeMillis();
						Thread.sleep(TIME_TO_FISH_BUFFER);
						state = State.FISH;
					}else{
						Thread.sleep(WAIT_CHECK_INTERVAL);
					}
					break;
				case FISH:
					long cur = System.currentTimeMillis();
					boolean failed = cur - fishingStarted > MAX_FISHING_TIME;
					boolean aboutTime = cur - fishingStarted > SOONEST_POSSIBLE_FISH_TIME;
					if(aboutTime && isFinishedFishing() || failed){
						TeraFish.pressKey(KeyEvent.VK_F, 0);
						state = State.FINISHED;
						pressing = false;
						Thread.sleep(AFTER_FISHING_DELAY);
					}else{
						continueFish();
					}
					break;
			}
			//long executionTime = System.currentTimeMillis() - startTime;
			//System.out.println("Execution time: " + executionTime + "ms");
		}
	}
	public static void continueFish()throws IOException{
		Image i = TeraFish.screenshot(GAUGE);
		Preprocess.histogram(i);
		Blob[] blobs = Blobbing.getBlobs(i);
		if(blobs.length==2){
			int box, fish;
			if(blobs[0].getTop() < blobs[1].getTop()){
				box = 0;
				fish = 1;
			}else{
				box = 1;
				fish = 0;
			}

			if(needCheckGolden){
				golden = blobs[fish].getBrightness(i) > MIN_GOLDEN_WHITENESS;
				needCheckGolden = false;
			}

			if(blobs[fish].getLeft() < (blobs[box].getRight() - 20)){
				if(TeraFish.debug){
					System.out.println("Behind, press F");
				}else{
					if(!pressing){
						TeraFish.r.keyPress(KeyEvent.VK_F);
						pressing = true;
					}
				}
			}else{
				if(TeraFish.debug){
					System.out.println("In front, release F");
				}else{
					if(pressing){
						TeraFish.r.keyRelease(KeyEvent.VK_F);
						pressing = false;
					}
				}
			}
		}else if(blobs.length==1){
			//search -8y, -8x of the edge
			//up to +2
			//not 15 px
			//>155
			if(fishTooFar(i, blobs[0])){
				if(TeraFish.debug){
					System.out.println("In, too far, release F");
				}else{
					if(pressing){
						TeraFish.r.keyRelease(KeyEvent.VK_F);
						pressing = false;
					}
				}
			}else{
				if(TeraFish.debug){
					System.out.println("In, not too far, hold F");
				}else{
					if(!pressing){
						TeraFish.r.keyPress(KeyEvent.VK_F);
						pressing = true;
					}
				}
			}
		}

		if(TeraFish.debug){
			TeraFish.printDebug(i, blobs);
		}
	}

	private static boolean fishTooFar(Image i, Blob b){
		int rightMost = b.getRight();
		int leftMost = b.getLeft();
		int minBrightness = 150;
		// Calling this function means they are in the same blob
		if(golden){
			minBrightness = 180;
			if(rightMost - leftMost < 50){
				// if only the fish is detected, keep holding F
				// Golden fish brightness is so high that the box disappeared when
				// golden is in the box
				return false;
			}
		}
		// if they are in the right-most of the pixels, just hold F
		if(rightMost==(GAUGE_WIDTH - 1)){
			return false;
		}
		int topMost = b.getTop();
		int left = leftMost + 10;
		int[][] grey = i.getGrey();
		if(grey[topMost][leftMost] < 50){
			// leftmost is definitely fish tail
			return false;
		}
		int top = topMost + 9;
		int right = rightMost - 10;
		int distance = golden ? 12 : 30;
		for(int ii = right; ii >= left; ii--){
			if(grey[top][ii] > minBrightness){
				//this is the fin
				//the head if golden
				if(rightMost - ii < distance){
					return true;
				}else{
					return false;
				}
			}
		}
		return false;
	}

	private static boolean isTimeToFish(){
		double ratio = Preprocess.sumOfAbsoluteRatio(
			fTpl,
			Preprocess.getGrey(TeraFish.screenshot(F))
		);

		return ratio < F_RATIO;
	}

	private static boolean isFinishedFishing(){
		double ratio = Preprocess.sumOfAbsoluteRatio(
			gaugeFrameTpl,
			(TeraFish.screenshot(GAUGE_FRAME)).getRgb()
		);

		return ratio > FRAME_RATIO;
	}
}

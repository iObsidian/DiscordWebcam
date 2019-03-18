package opencv;

import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import test.Constants;
import test.NetworkCamera;
import test.ui.EditCameraUI;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class CameraPanel extends JInternalFrame {
	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		System.loadLibrary("opencv_ffmpeg342_64"); //crucial to use IP Camera
	}

	private NetworkCamera networkCamera;

	private Boolean begin = false;
	private Boolean firstFrame = true;
	private VideoCapture video = null;
	private MatOfByte matOfByte = new MatOfByte();
	private BufferedImage bufImage = null;
	private InputStream in;
	private Mat frameaux = new Mat();
	private Mat frame = new Mat(240, 320, CvType.CV_8UC3);
	private Mat lastFrame = new Mat(240, 320, CvType.CV_8UC3);
	private Mat currentFrame = new Mat(240, 320, CvType.CV_8UC3);
	private Mat processedFrame = new Mat(240, 320, CvType.CV_8UC3);
	private ImagePanel image;
	private int savedelay = 0;
	String currentDir = "";
	String detectionsDir = "detections";

	CaptureThread thread;

	JMenuBar menuBar;

	public static void main(String[] args) {
		new CameraPanel(new NetworkCamera("Test camera", "http://192.168.0.107:8080/video"
		), () -> System.out.println("On close"));
	}

	public CameraPanel(NetworkCamera n, Runnable onRemove) {

		super(n.name, true, true, true, true);

		this.networkCamera = n;

		setBackground(Constants.CAMERA_PANEL_BACKGROUND_COLOR);

		setSize(n.width, n.height);
		setLocation(n.x, n.y);

		setResizable(true);
		setLayout(new BorderLayout());

		addInternalFrameListener(new InternalFrameAdapter() {
			@Override
			public void internalFrameActivated(InternalFrameEvent e) {
				super.internalFrameActivated(e);
				receivedFocus();
			}

			@Override
			public void internalFrameDeactivated(InternalFrameEvent e) {
				super.internalFrameDeactivated(e);
				lostFocus();
			}
		});

		addInternalFrameListener(new InternalFrameAdapter() {
			public void internalFrameClosing(InternalFrameEvent e) {
				onRemove.run();
			}
		});

		addComponentListener(new ComponentAdapter() {
			public void componentMoved(ComponentEvent e) {
				super.componentMoved(e);

				n.x = getX();
				n.y = getY();
			}

			@Override
			public void componentResized(ComponentEvent e) {
				super.componentResized(e);

				n.height = getHeight();
				n.width = getWidth();
			}
		});

		setFrameIcon(new ImageIcon(Constants.cameraIcon));

		image = new ImagePanel(new ImageIcon("figs/320x240.gif").getImage());
		setLayout(new BorderLayout());
		add(image, BorderLayout.CENTER);

		menuBar = new JMenuBar();
		menuBar.setVisible(false);

		JMenu settings = new JMenu("Settings");
		settings.setIcon(new ImageIcon(Constants.gearIcon));
		menuBar.add(settings);

		settings.addMenuListener(new MenuListener() {
			@Override
			public void menuSelected(MenuEvent e) {
				System.out.println("Opening settings");
				new EditCameraUI(n, new Runnable() {
					@Override
					public void run() {
						setTitle(n.name);
					}
				});
			}

			@Override
			public void menuDeselected(MenuEvent e) {
			}

			@Override
			public void menuCanceled(MenuEvent e) {
			}
		});

		JMenu disable = new JMenu("Disable");

		disable.addMenuListener(new MenuListener() {
			@Override
			public void menuSelected(MenuEvent e) {
				n.toggleEnabled();

				if (n.isEnabled) {
					disable.setText("Disable");
					setFrameIcon(new ImageIcon(Constants.cameraIcon));
					settings.setIcon(new ImageIcon(Constants.gearIcon));
				} else {
					disable.setText("Enable");
					setFrameIcon(new ImageIcon(Constants.cameraIconGrayScale));
					settings.setIcon(new ImageIcon(Constants.gearIconGrayScale));
				}
			}

			@Override
			public void menuDeselected(MenuEvent e) {
			}

			@Override
			public void menuCanceled(MenuEvent e) {
			}
		});

		menuBar.add(disable);

		add(menuBar, BorderLayout.SOUTH);

		currentDir = Paths.get(".").toAbsolutePath().normalize().toString();
		detectionsDir = currentDir + File.separator + detectionsDir;
		System.out.println("Current dir: " + currentDir);
		System.out.println("Detections dir: " + detectionsDir);

		setVisible(true);

		start();
	}

	public void receivedFocus() {
		menuBar.setVisible(true);
	}

	public void lostFocus() {
		menuBar.setVisible(false);
	}

	private void start() {

		if (!begin) {

			video = new VideoCapture(networkCamera.networkAddress);

			double dWidth = video.get(Videoio.CV_CAP_PROP_FRAME_WIDTH); //get the width of frames of the video
			double dHeight = video.get(Videoio.CV_CAP_PROP_FRAME_HEIGHT); //get the height of frames of the video

			setPreferredSize(new Dimension((int) dWidth, (int) dHeight));

			System.out.println("Got width : " + dWidth + ", height : " + dHeight);

			if (video.isOpened()) {
				System.out.println("Is opened");

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				thread = new CaptureThread();
				thread.start();
				begin = true;
				firstFrame = true;
			}
		}
	}

	/**
	 * Disposes of this object
	 */
	private void end() {

		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (begin) {
			try {
				Thread.sleep(500);
			} catch (Exception ex) {
			}
			video.release();
			begin = false;

		}
	}

	public static String getCurrentTimeStamp() {
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");//dd/MM/yyyy
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}

	public ArrayList<Rect> detection_contours(Mat frame, Mat outmat) {
		Mat v = new Mat();
		Mat vv = outmat.clone();
		List<MatOfPoint> contours = new ArrayList<>();
		Imgproc.findContours(vv, contours, v, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

		double maxArea = 100;
		int maxAreaIdx;
		Rect r;
		ArrayList<Rect> rect_array = new ArrayList<>();

		for (int idx = 0; idx < contours.size(); idx++) {
			Mat contour = contours.get(idx);
			double contourarea = Imgproc.contourArea(contour);
			if (contourarea > maxArea) {
				// maxArea = contourarea;
				maxAreaIdx = idx;
				r = Imgproc.boundingRect(contours.get(maxAreaIdx));
				rect_array.add(r);
				Imgproc.drawContours(frame, contours, maxAreaIdx, new Scalar(0, 0, 255));
			}
		}

		v.release();
		return rect_array;
	}

	private boolean alarm;

	boolean save = false;

	class CaptureThread extends Thread {

		@Override
		public void run() {
			if (video.isOpened()) {
				while (begin) {
					video.read(frameaux);

					//TODO why does read work, but not retrieve?

					//video.retrieve(frameaux);
					Imgproc.resize(frameaux, frame, frame.size());
					frame.copyTo(currentFrame);

					if (firstFrame) {
						frame.copyTo(lastFrame);
						firstFrame = false;
						continue;
					}

					if (networkCamera.motionDetection) {
						Imgproc.GaussianBlur(currentFrame, currentFrame, new Size(3, 3), 0);
						Imgproc.GaussianBlur(lastFrame, lastFrame, new Size(3, 3), 0);

						//bsMOG.apply(frame, processedFrame, 0.005);
						Core.subtract(currentFrame, lastFrame, processedFrame);
						//Core.absdiff(frame,lastFrame,processedFrame);

						Imgproc.cvtColor(processedFrame, processedFrame, Imgproc.COLOR_RGB2GRAY);
						//

						//Imgproc.adaptiveThreshold(processedFrame, processedFrame, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 5, 2);
						Imgproc.threshold(processedFrame, processedFrame, networkCamera.threshold, 255, Imgproc.THRESH_BINARY);

						ArrayList<Rect> array = detection_contours(currentFrame, processedFrame);
						///*
						if (array.size() > 0) {
							Iterator<Rect> it2 = array.iterator();
							while (it2.hasNext()) {
								Rect obj = it2.next();
								Imgproc.rectangle(currentFrame, obj.br(), obj.tl(),
										new Scalar(0, 255, 0), 1);

							}
						}
						//*/

						if (alarm) {
							double sensibility = 15;
							//System.out.println(sensibility);
							double nonZeroPixels = Core.countNonZero(processedFrame);
							//System.out.println("nonZeroPixels: " + nonZeroPixels);

							double nrows = processedFrame.rows();
							double ncols = processedFrame.cols();
							double total = nrows * ncols / 10;

							double detections = (nonZeroPixels / total) * 100;
							//System.out.println(detections);
							if (detections >= sensibility) {
								//System.out.println("ALARM ENABLED!");
								Imgproc.putText(currentFrame, "MOTION DETECTED",
										new Point(5, currentFrame.cols() / 2), //currentFrame.rows()/2 currentFrame.cols()/2
										Core.FONT_HERSHEY_TRIPLEX, 1d, new Scalar(0, 0, 255));

								if (save) {
									if (savedelay == 2) {
										System.out.println("Saving results in: " + detectionsDir);
										Imgcodecs.imwrite(detectionsDir, processedFrame);
										savedelay = 0;
									} else
										savedelay = savedelay + 1;
								}
							} else {
								savedelay = 0;
								//System.out.println("");
							}
						}

						//currentFrame.copyTo(processedFrame);

					} else {

						//frame.copyTo(processedFrame);

					}

					currentFrame.copyTo(processedFrame);

					Imgcodecs.imencode(".jpg", processedFrame, matOfByte);
					byte[] byteArray = matOfByte.toArray();

					try {
						in = new ByteArrayInputStream(byteArray);
						bufImage = ImageIO.read(in);
					} catch (Exception ex) {
						ex.printStackTrace();
					}

					//image.updateImage(new ImageIcon("figs/lena.png").getImage());
					image.updateImage(bufImage);

					frame.copyTo(lastFrame);

					try {
						Thread.sleep(1);
					} catch (Exception ex) {
					}
				}
			}
		}
	}

}

class ImagePanel extends JPanel {
	private Image img;

	public ImagePanel(Image img) {
		this.img = img;
		Dimension size = new Dimension(img.getWidth(null), img.getHeight(null));
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setSize(size);
		setLayout(null);
		//setBorder(BorderFactory.createLineBorder(Color.black));
	}

	public void updateImage(Image img) {
		this.img = img;
		validate();
		repaint();
	}

	@Override
	public void paintComponent(Graphics g) {
		g.drawImage(img, 0, 0, null);
	}
}
package application;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import ws.schild.jave.AudioAttributes;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.EncoderProgressListener;
import ws.schild.jave.EncodingAttributes;
import ws.schild.jave.MultimediaInfo;
import ws.schild.jave.MultimediaObject;

public class WaveFormService extends Service<Boolean> {
	
	private static final double WAVEFORM_HEIGHT_COEFFICIENT = 2.5; // This fits the waveform to the swing node height
	private static final CopyOption[] options = new CopyOption[]{ COPY_ATTRIBUTES , REPLACE_EXISTING };
	private float[] resultingWaveform;
	private int[] wavAmplitudes;
	private String fileAbsolutePath;
	private final WaveVisualization waveVisualization;
	private final Random random = new Random();
	private File temp1;
	private File temp2;
	private Encoder encoder;
	private ConvertProgressListener listener = new ConvertProgressListener();
	private WaveFormJob waveJob;
	
	/**
	 * Wave Service type of Job ( not boob job ... )
	 * 
	 * @author GOXR3PLUSSTUDIO
	 *
	 */
	public enum WaveFormJob {
		AMPLITUDES_AND_AMPLITUDES, WAVEFORM;
	}
	
	/**
	 * Constructor.
	 */
	public WaveFormService(WaveVisualization waveVisualization) {
		this.waveVisualization = waveVisualization;
		
		setOnSucceeded(s -> done());
		setOnFailed(f -> failure());
		setOnCancelled(c -> failure());
	}
	
	/**
	 * Start the external Service Thread.
	 *
	 * 
	 */
	public void startService(String fileAbsolutePath , WaveFormJob waveJob) {
		
		//Check if boob job
		this.waveJob = waveJob;
		
		//Stop the Serivce
		waveVisualization.stopPainterService();
		
		//Variables
		this.fileAbsolutePath = fileAbsolutePath;
		this.resultingWaveform = null;
		this.wavAmplitudes = null;
		
		//Go
		restart();
	}
	
	/**
	 * Done.
	 */
	// Work done
	public void done() {
		waveVisualization.startPainterService();
		deleteTemporaryFiles();
	}
	
	private void failure() {
		deleteTemporaryFiles();
	}
	
	/**
	 * Delete temporary files
	 */
	private void deleteTemporaryFiles() {
		temp1.delete();
		temp2.delete();
	}
	
	@Override
	protected Task<Boolean> createTask() {
		return new Task<Boolean>() {
			
			@Override
			protected Boolean call() throws Exception {
				
				try {
					
					//Calculate 
					if (waveJob == WaveFormJob.AMPLITUDES_AND_AMPLITUDES) { //AMPLITUDES_AND_AMPLITUDES
						String fileFormat = "mp3";
						resultingWaveform = processFromNoWavFile(fileFormat);
						
					} else if (waveJob == WaveFormJob.WAVEFORM) { //WAVEFORM
						resultingWaveform = processAmplitudes(wavAmplitudes);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					return false;
				}
				
				return true;
				
			}
			
			//			private float[] processFromWavFile() throws IOException , UnsupportedAudioFileException {
			//				File trackFile = new File(trackToAnalyze.getFileFolder(), trackToAnalyze.getFileName());
			//				return processAmplitudes(getWavAmplitudes(trackFile));
			//			}
			
			/**
			 * Try to process a Non Wav File
			 * 
			 * @param fileFormat
			 * @return
			 * @throws IOException
			 * @throws UnsupportedAudioFileException
			 * @throws EncoderException
			 */
			private float[] processFromNoWavFile(String fileFormat) throws IOException , UnsupportedAudioFileException , EncoderException {
				int randomN = random.nextInt(99999);
				
				//Create temporary files
				File temporalDecodedFile = File.createTempFile("decoded_" + randomN, ".wav");
				File temporalCopiedFile = File.createTempFile("original_" + randomN, "." + fileFormat);
				temp1 = temporalDecodedFile;
				temp2 = temporalCopiedFile;
				
				//Delete temporary Files on exit
				temporalDecodedFile.deleteOnExit();
				temporalCopiedFile.deleteOnExit();
				
				//Create a temporary path
				Files.copy(new File(fileAbsolutePath).toPath(), temporalCopiedFile.toPath(), options);
				
				//Transcode to .wav
				transcodeToWav(temporalCopiedFile, temporalDecodedFile);
				
				//Avoid creating amplitudes again for the same file
				if (wavAmplitudes == null)
					wavAmplitudes = getWavAmplitudes(temporalDecodedFile);
				
				//Delete temporary files
				temporalDecodedFile.delete();
				temporalCopiedFile.delete();
				
				return processAmplitudes(wavAmplitudes);
			}
			
			/**
			 * Get Wav Amplitudes
			 * 
			 * @param file
			 * @return
			 * @throws UnsupportedAudioFileException
			 * @throws IOException
			 */
			private int[] getWavAmplitudes(File file) throws UnsupportedAudioFileException , IOException {
				System.out.println("Calculting amplitudes");
				int[] amplitudes = null;
				
				//Get Audio input stream
				try (AudioInputStream input = AudioSystem.getAudioInputStream(file)) {
					AudioFormat baseFormat = input.getFormat();
					
					Encoding encoding = AudioFormat.Encoding.PCM_UNSIGNED;
					float sampleRate = baseFormat.getSampleRate();
					int numChannels = baseFormat.getChannels();
					
					AudioFormat decodedFormat = new AudioFormat(encoding, sampleRate, 16, numChannels, numChannels * 2, sampleRate, false);
					int available = input.available();
					amplitudes = new int[available];
					
					//Get the PCM Decoded Audio Input Stream
					try (AudioInputStream pcmDecodedInput = AudioSystem.getAudioInputStream(decodedFormat, input)) {
						final int BUFFER_SIZE = 4096; //this is actually bytes
						System.out.println(available);
						
						//Create a buffer
						byte[] buffer = new byte[BUFFER_SIZE];
						
						//Read all the available data on chunks
						int counter = 0;
						while (pcmDecodedInput.readNBytes(buffer, 0, BUFFER_SIZE) > 0)
							for (int i = 0; i < buffer.length - 1; i += 2, counter += 2) {
								if (counter == available)
									break;
								amplitudes[counter] = ( ( buffer[i + 1] << 8 ) | buffer[i] & 0xff ) << 16;
								amplitudes[counter] /= 32767;
								amplitudes[counter] *= WAVEFORM_HEIGHT_COEFFICIENT;
							}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				
				System.out.println("Finished Calculting amplitudes");
				return amplitudes;
			}
			
			/**
			 * Transcode to Wav
			 * 
			 * @param sourceFile
			 * @param destinationFile
			 * @throws EncoderException
			 */
			private void transcodeToWav(File sourceFile , File destinationFile) throws EncoderException {
				//Attributes atters = DefaultAttributes.WAV_PCM_S16LE_STEREO_44KHZ.getAttributes()
				try {
					
					//Set Audio Attributes
					AudioAttributes audio = new AudioAttributes();
					audio.setCodec("pcm_s16le");
					audio.setChannels(2);
					audio.setSamplingRate(44100);
					
					//Set encoding attributes
					EncodingAttributes attributes = new EncodingAttributes();
					attributes.setFormat("wav");
					attributes.setAudioAttributes(audio);
					
					//Encode
					encoder = encoder != null ? encoder : new Encoder();
					encoder.encode(new MultimediaObject(sourceFile), destinationFile, attributes, listener);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		};
	}
	
	/**
	 * Process the amplitudes
	 * 
	 * @param sourcePcmData
	 * @return An array with amplitudes
	 */
	public float[] processAmplitudes(int[] sourcePcmData) {
		System.out.println("Processing amplitudes");
		
		//The width of the resulting waveform panel
		int width = waveVisualization.width;
		float[] waveData = new float[width];
		int samplesPerPixel = sourcePcmData.length / width;
		
		//Calculate
		float nValue;
		for (int w = 0; w < width; w++) {
			
			//For performance keep it here
			int c = w * samplesPerPixel;
			nValue = 0.0f;
			
			//Keep going
			for (int s = 0; s < samplesPerPixel; s++) {
				nValue += ( Math.abs(sourcePcmData[c + s]) / 65536.0f );
			}
			
			//Set WaveData
			waveData[w] = nValue / samplesPerPixel;
		}
		
		System.out.println("Finished Processing amplitudes");
		return waveData;
	}
	
	public class ConvertProgressListener implements EncoderProgressListener {
		int current = 1;
		
		public ConvertProgressListener() {
		}
		
		public void message(String m) {
		}
		
		public void progress(int p) {
			
			double progress = p / 1000.00;
			System.out.println(progress);
			
		}
		
		public void sourceInfo(MultimediaInfo m) {
		}
	}
	
	public String getFileAbsolutePath() {
		return fileAbsolutePath;
	}
	
	public void setFileAbsolutePath(String fileAbsolutePath) {
		this.fileAbsolutePath = fileAbsolutePath;
	}
	
	public int[] getWavAmplitudes() {
		return wavAmplitudes;
	}
	
	public float[] getResultingWaveform() {
		return resultingWaveform;
	}
	
	public void setResultingWaveform(float[] resultingWaveform) {
		this.resultingWaveform = resultingWaveform;
	}
	
}

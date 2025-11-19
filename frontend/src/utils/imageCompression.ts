import imageCompression from 'browser-image-compression';

const MAX_ORIGINAL_SIZE_MB = 10; // Accept files up to 10 MB
const MAX_COMPRESSED_SIZE_MB = 5; // Compress down to 5 MB max
const MAX_WIDTH_HEIGHT = 2048; // Max dimension

export const validateAndCompressImage = async (
  file: File
): Promise<{ success: boolean; compressedFile?: File; error?: string }> => {

  // Validate file type
  if (!file.type.startsWith('image/')) {
    return { success: false, error: 'Please select an image file' };
  }

  // Check original file size
  const fileSizeMB = file.size / 1024 / 1024;
  if (fileSizeMB > MAX_ORIGINAL_SIZE_MB) {
    return {
      success: false,
      error: `File too large (${fileSizeMB.toFixed(1)} MB). Maximum is ${MAX_ORIGINAL_SIZE_MB} MB`
    };
  }

  try {
    const options = {
      maxSizeMB: MAX_COMPRESSED_SIZE_MB,
      maxWidthOrHeight: MAX_WIDTH_HEIGHT,
      useWebWorker: false, // Disable to prevent HMR spam during development
      initialQuality: 0.9,
      alwaysKeepResolution: false
    };

    const compressedFile = await imageCompression(file, options);

    const compressedSizeMB = compressedFile.size / 1024 / 1024;
    console.log(`Image compressed: ${fileSizeMB.toFixed(2)} MB → ${compressedSizeMB.toFixed(2)} MB`);

    return { success: true, compressedFile };
  } catch (error) {
    console.error('Compression error:', error);
    return {
      success: false,
      error: 'Failed to compress image. Please try a different image.'
    };
  }
};

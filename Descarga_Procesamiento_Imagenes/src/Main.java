
/*
* INTEGRANTES DEL EQUIPO
* - PICHAL PIO JOSE ARMANDO
* - CANELA BELTRAN JOSE
* */


import javax.imageio.ImageIO; // Para leer y escribir las imagenes
import java.awt.*;  //Para manejar los graficos y colores
import java.awt.image.*; //Para manejar las imagenes y filtros
import java.io.*; //Para las operaciones de entrada y salida de archivos
import java.net.URL;  //Para manejar las URLs
import java.nio.file.*;  //Para manipulae el siema de archivos
import java.util.List;  //Lista de URLs
import java.util.Map;  // Mapa para asociar nombre de la imagen -> URL
import java.util.concurrent.*;  //Para manejar ejecucion con los hilos

public class Main {
    //Carpeta donde se guardaran las imagenes procesadas
    private static final String OUTPUT_DIR = "imagenes_filtradas";

    public static void main(String[] args) throws Exception {
        // Crear la carpeta de salida si no existe
        Files.createDirectories(Paths.get(OUTPUT_DIR));

        // Lee todas las lineas del los URLs desde el archivo
        List<String> urls = Files.readAllLines(Paths.get("urls.txt"));

        //Mapa para asociar el nombre a cada URL
        Map<String, String> urlMap = new ConcurrentHashMap<>();
        int counter = 1;
        for (String url : urls) {
            urlMap.put("image" + counter++, url);
        }

        // Para descragar las imagenes
        ExecutorService downloadExecutor = Executors.newFixedThreadPool(5);
        //Para aplicar los filtros
        ExecutorService filterExecutor = Executors.newFixedThreadPool(5);
        //Para guardar las imagenes
        ExecutorService saveExecutor = Executors.newFixedThreadPool(5);

        //Recorre cada imagen-URL en el mapa
        for (Map.Entry<String, String> entry : urlMap.entrySet()) {
            downloadExecutor.submit(() -> {
                try {
                    //Descarga la imagen desde la URL
                    BufferedImage img = ImageIO.read(new URL(entry.getValue()));
                    if (img != null) {
                        // Descargada la imagenn, aplica filtros en otro hilo
                        filterExecutor.submit(() -> applyFilters(entry.getKey(), img, saveExecutor));
                    } else {
                        System.err.println("No se pudo leer imagen: " + entry.getValue());
                    }
                } catch (IOException e) {
                    System.err.println("Descargando imagen: " + entry.getValue());
                }
            });
        }

        // Apagar ejecutores
        downloadExecutor.shutdown();
        downloadExecutor.awaitTermination(1, TimeUnit.HOURS);
        filterExecutor.shutdown();
        filterExecutor.awaitTermination(1, TimeUnit.HOURS);
        saveExecutor.shutdown();
        saveExecutor.awaitTermination(1, TimeUnit.HOURS);

        System.out.println("Proceso completado.");
    }

    private static void applyFilters(String name, BufferedImage img, ExecutorService saveExecutor) {
        BufferedImage sepia = applySepiaFilter(img); //aplica filtro seria
        BufferedImage sharpen = applySharpenFilter(img); //aplica filtros de enfoque
        BufferedImage bw = applyBlackAndWhiteFilter(img); // aplica filtro blanco y negro

        // Manda a guardar cada imagen procesada en un hilo separado
        saveExecutor.submit(() -> saveImage(sepia, name + "_sepia"));
        saveExecutor.submit(() -> saveImage(sharpen, name + "_sharpen"));
        saveExecutor.submit(() -> saveImage(bw, name + "_bw"));
    }

    // Guardar una imagen en el direcctorio de salida con el nombre dado
    private static void saveImage(BufferedImage img, String filename) {
        try {
            File output = new File(OUTPUT_DIR, filename + ".png");
            ImageIO.write(img, "png", output);
            System.out.println("Imagen guardada: " + output.getPath());
        } catch (IOException e) {
            System.err.println("Error guardando imagen: " + filename);
        }
    }

    // Filtro Sepia
    private static BufferedImage applySepiaFilter(BufferedImage img) {
        BufferedImage sepia = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                //formulas de sepia
                int tr = (int)(0.393 * c.getRed() + 0.769 * c.getGreen() + 0.189 * c.getBlue());
                int tg = (int)(0.349 * c.getRed() + 0.686 * c.getGreen() + 0.168 * c.getBlue());
                int tb = (int)(0.272 * c.getRed() + 0.534 * c.getGreen() + 0.131 * c.getBlue());
                //Asegura los valores esten entre 0 y 255
                sepia.setRGB(x, y, new Color(clamp(tr), clamp(tg), clamp(tb)).getRGB());
            }
        }
        return sepia;
    }

    // Filtro Sharpen
    private static BufferedImage applySharpenFilter(BufferedImage img) {
        // Kernel de enfoque (sharpen)
        float[] sharpenKernel = {
                0.f, -1.f, 0.f,
                -1.f, 5.f, -1.f,
                0.f, -1.f, 0.f
        };
        BufferedImageOp op = new ConvolveOp(new Kernel(3, 3, sharpenKernel));
        return op.filter(img, null);
    }

    // Filtro Black and White
    private static BufferedImage applyBlackAndWhiteFilter(BufferedImage img) {
        //crea la imagen en escalas de grises
        BufferedImage bw = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = bw.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return bw;
    }

    //Asegura que los valores del los colores entene ntre 0 y 255
    private static int clamp(int val) {
        return Math.min(255, Math.max(0, val));
    }
}

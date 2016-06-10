package ua.deth.server;

import jdk.nashorn.internal.objects.Global;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by Deth on 08.06.2016.
 */
public class JavaWebServer extends Thread {
    Socket socket;

    public JavaWebServer(Socket socket) {
        this.socket = socket;
        setDaemon(true);
        setPriority(NORM_PRIORITY);
        start();
    }

    public void run() {
        try {
            // Подключаем читателей и писателей потока
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Получаем путь к запрашиваемому файлу
            byte[] bytes = new byte[64*1024];
            int size = in.read(bytes);
            String request = new String(bytes,0,size);

            String pathToFile = getPath(request);
            // Если путьпустой то выдаем 400
            if(pathToFile == null)
            {
                // первая строка ответа
                String response = "HTTP/1.1 400 Bad Request\n";

                // дата в GMT
                DateFormat df = DateFormat.getTimeInstance();
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                response = response + "Date: " + df.format(new Date()) + "\n";

                // остальные заголовки
                response = response
                        + "Connection: close\n"
                        + "Server: JavaWebServer\n"
                        + "Pragma: no-cache\n\n";

                // выводим данные:
                out.write(response.getBytes());
                out.flush();
                // завершаем соединение
                socket.close();
                // выход
                return;
            }
            // если файл существует и является директорией,
            // то ищем индексный файл index.html
            File file = new File(pathToFile);
            boolean flag = !file.exists();
            if(!flag) if(file.isDirectory())
            {
                if(pathToFile.lastIndexOf(""+File.separator) == pathToFile.length()-1)
                    pathToFile = pathToFile + "index.html";
                else
                    pathToFile = pathToFile + File.separator + "index.html";
                file = new File(pathToFile);
                flag = !file.exists();
            }

            // если по указанному пути файл не найден
            // то выводим ошибку "404 Not Found"
            if(flag)
            {
                // первая строка ответа
                String response = "HTTP/1.1 404 Not Found\n";

                // дата в GMT
                DateFormat dateFormat = DateFormat.getTimeInstance();
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                response = response + "Date: " + dateFormat.format(new Date()) + "\n";

                // остальные заголовки
                response = response
                        + "Content-Type: text/plain\n"
                        + "Connection: close\n"
                        + "Server: JavaWebServer\n"
                        + "Pragma: no-cache\n\n";

                // и гневное сообщение
                response = response + "File " + pathToFile + " not found!";

                // выводим данные:
                out.write(response.getBytes());
               // out.flush();
                // завершаем соединение
                socket.close();

                // выход
                return;
            }

            // определяем MIME файла по расширению
            // MIME по умолчанию - "text/plain"
            String mime = "text/plain";

            // выделяем у файла расширение (по точке)
            size = pathToFile.lastIndexOf(".");
            if(size > 0)
            {
                String ext = pathToFile.substring(size);
                System.out.print(ext);
                if(ext.equalsIgnoreCase(".html"))
                    mime = "text/html";
                else if(ext.equalsIgnoreCase(".htm"))
                    mime = "text/html";
                else if(ext.equalsIgnoreCase(".gif"))
                    mime = "image/gif";
                else if(ext.equalsIgnoreCase(".jpg"))
                    mime = "image/jpeg";
                else if(ext.equalsIgnoreCase(".jpeg"))
                    mime = "image/jpeg";
                else if(ext.equalsIgnoreCase(".bmp"))
                    mime = "image/x-xbitmap";
            }

            // создаём ответ

            // первая строка ответа
            String response = "HTTP/1.1 200 OK\n";
            int fileSize = (int) file.length()+2;
            // дата создания в GMT
            DateFormat df = DateFormat.getTimeInstance();
            df.setTimeZone(TimeZone.getTimeZone("GMT"));

            // время последней модификации файла в GMT
            response = response + "Last-Modified: " + df.format(new Date(file.lastModified())) + "\n";

            // длина файла
            response = response + "Content-Length: " +file.length() + "\n";

            // строка с MIME кодировкой
            response = response + "Content-Type: " + mime + "\n";

            // остальные заголовки
            response = response
                    + "Connection: close\n"
                    + "Server: JavaWebServer\n\n";

            // выводим заголовок:
            out.write(response.getBytes());
           // out.flush();

            // и сам файл:
            FileInputStream fis = new FileInputStream(pathToFile);
            size = 1;
            while(size > 0)
            {
                size = fis.read();
                if(size > 0) {
                    out.write(size);
                   // out.flush();
                }
            }
            fis.close();

            // завершаем соединение
            socket.close();
        } catch (Exception e) {
            //Ловим любое исключение
            System.err.println("Some's going wrong" + e);
        }
    }

    protected String getPath(String header) {
        // ищем URI, указанный в HTTP запросе
        // URI ищется только для методов POST и GET, иначе возвращается null
        String URI = extract(header, "GET ", " "), path;
        if (URI == null) URI = extract(header, "POST ", " ");
        if (URI == null) return null;

        // если URI записан вместе с именем протокола
        // то удаляем протокол и имя хоста
        path = URI.toLowerCase();
        if (path.indexOf("http://", 0) == 0) {
            URI = URI.substring(7);
            URI = URI.substring(URI.indexOf("/", 0));
        } else if (path.indexOf("/", 0) == 0)
            URI = URI.substring(1); // если URI начинается с символа /, удаляем его

        // отсекаем из URI часть запроса, идущего после символов ? и #
        int i = URI.indexOf("?");
        if (i > 0) URI = URI.substring(0, i);
        i = URI.indexOf("#");
        if (i > 0) URI = URI.substring(0, i);

        // конвертируем URI в путь до документов
        // предполагается, что документы лежат там же, где и сервер
        // иначе ниже нужно переопределить path
        path = "." + File.separator;
        char a;
        for (i = 0; i < URI.length(); i++) {
            a = URI.charAt(i);
            if (a == '/')
                path = path + File.separator;
            else
                path = path + a;
        }

        return path;
    }

    protected String extract(String str, String start, String end) {
        int s = str.indexOf("\n\n", 0), e;
        if (s < 0) s = str.indexOf("\r\n\r\n", 0);
        if (s > 0) str = str.substring(0, s);
        s = str.indexOf(start, 0) + start.length();
        if (s < start.length()) return null;
        e = str.indexOf(end, s);
        if (e < 0) e = str.length();
        return (str.substring(s, e)).trim();
    }

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(880, 0);
            System.out.println("Server is started");
            while (true) {
                // Создаем поток,для нового клиента
                new JavaWebServer(serverSocket.accept());
            }
        } catch (IOException e) {
            e.printStackTrace();

        }


    }
}

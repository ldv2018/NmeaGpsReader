package com.example;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.io.InputStream;

public class NmeaGpsReader {

    // Параметры порта по умолчанию
    private static final int DEFAULT_BAUDRATE = 9600;
    private static final String DEFAULT_PORT = "/COM7"; //"COM_" для Windows

    public static void main(String[] args) {
        String portName = DEFAULT_PORT;
        int baudRate = DEFAULT_BAUDRATE;

        // Обработка аргументов командной строки
        if (args.length > 0) {
            portName = args[0];
        }
        if (args.length > 1) {
            try {
                baudRate = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Неверная скорость порта, используется " + DEFAULT_BAUDRATE);
            }
        }

        System.out.println("=== NMEA GPS Reader ===");
        System.out.println("Порт: " + portName);
        System.out.println("Скорость: " + baudRate + " бод");
        System.out.println("Нажмите Ctrl+C для выхода.\n");

        // Получение экземпляра последовательного порта
        SerialPort comPort = SerialPort.getCommPort(portName);
        comPort.setBaudRate(baudRate);
        comPort.setNumDataBits(8);
        comPort.setNumStopBits(1);
        comPort.setParity(SerialPort.NO_PARITY);
        comPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

        // Открытие порта
        if (!comPort.openPort()) {
            System.err.println("Не удалось открыть порт " + portName);
            System.exit(1);
        }

        // Добавление слушателя событий (чтение данных)
        comPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                    return;

                byte[] buffer = new byte[4096];
                InputStream input = comPort.getInputStream();
                try {
                    // Читаем доступные байты
                    while (input.available() > 0) {
                        int numRead = input.read(buffer);
                        // Преобразуем байты в строку и обрабатываем по строкам
                        String data = new String(buffer, 0, numRead);
                        processNmeaStream(data);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Буфер для накопления строки (NMEA предложения разделяются \r\n)
            private StringBuilder lineBuffer = new StringBuilder();

            /**
             * Обрабатывает поток данных, собирает полные строки и передаёт их в парсер.
             */
            private void processNmeaStream(String chunk) {
                for (char c : chunk.toCharArray()) {
                    if (c == '\n') {
                        String line = lineBuffer.toString().trim();
                        lineBuffer.setLength(0);
                        if (!line.isEmpty()) {
                            parseAndPrintNmea(line);
                        }
                    } else if (c != '\r') {
                        lineBuffer.append(c);
                    }
                }
            }
        });

        // Бесконечное ожидание (программа завершится по Ctrl+C)
        try {
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (comPort.closePort()) {
                System.out.println("Порт закрыт.");
            }
        }
    }

    /**
     * Парсит NMEA-строку и выводит информацию в консоль.
     */
    private static void parseAndPrintNmea(String nmea) {
        // Вывод сырой строки
        //System.out.println("RAW: " + nmea);

        // Проверка начала с '$'
        if (!nmea.startsWith("$")) {
            return;
        }

        // Проверка контрольной суммы
        if (!validateChecksum(nmea)) {
            System.err.println("Ошибка контрольной суммы: " + nmea);
            return;
        }

        // Разделение на поля
        String[] tokens = nmea.split(",");
        String type = tokens[0];

        // Обработка разных типов предложений
        switch (type) {
            case "$GPGGA":
                break;
            case "$GNGGA":
                parseGGA(tokens);
                break;
            case "$GPRMC":
            case "$GNRMC":
                //parseRMC(tokens);
                break;
            case "$GPGSV":
            case "$GNGSV":
                //parseGSV(tokens);
                break;
            case "$GPGSA":
            case "$GNGSA":
                //parseGSA(tokens);
                break;
            default:
                // Для других типов просто выводим тип
                //System.out.println("  Тип: " + type);
                break;
        }
        //System.out.println("----------------------------------------");
    }

    /**
     * Проверка NMEA контрольной суммы.
     */
    private static boolean validateChecksum(String nmea) {
        int starIdx = nmea.indexOf('*');
        if (starIdx == -1) return true;

        String data = nmea.substring(1, starIdx);
        String checksumStr = nmea.substring(starIdx + 1);
        if (checksumStr.length() < 2) return false;

        int calculated = 0;
        for (char c : data.toCharArray()) {
            calculated ^= c;
        }
        String hex = Integer.toHexString(calculated).toUpperCase();
        if (hex.length() == 1) hex = "0" + hex;
        return hex.equals(checksumStr);
    }

    /**
     * Парсинг GGA – фиксированные данные.
     */
    private static void parseGGA(String[] tokens) {
        System.out.println("  Тип: GGA (Fix Data)");
        if (tokens.length < 15) return;

        // Время UTC
        if (tokens.length > 1 && !tokens[1].isEmpty()) {
            String time = tokens[1];
            String formattedTime = time.substring(0, 2) + ":" +
                    time.substring(2, 4) + ":" +
                    time.substring(4, 6);
            System.out.println("  Время: " + formattedTime);
        }

        // Широта
        if (tokens.length > 2 && !tokens[2].isEmpty() && !tokens[3].isEmpty()) {
            double lat = convertNmeaCoordinate(tokens[2], tokens[3]);
            System.out.printf("  Широта: %.6f%n", lat);
        }

        // Долгота
        if (tokens.length > 4 && !tokens[4].isEmpty() && !tokens[5].isEmpty()) {
            double lon = convertNmeaCoordinate(tokens[4], tokens[5]);
            System.out.printf("  Долгота: %.6f%n", lon);
        }

        // Качество Fix
        if (tokens.length > 6 && !tokens[6].isEmpty()) {
            int quality = Integer.parseInt(tokens[6]);
            String qualityDesc = switch (quality) {
                case 0 -> "Нет фиксации";
                case 1 -> "GPS (SPS)";
                case 2 -> "Дифференциальный GPS";
                case 3 -> "PPS";
                case 4 -> "RTK Fixed";
                case 5 -> "RTK Float";
                case 6 -> "Оценочный";
                default -> "Неизвестно";
            };
            System.out.println("  Качество: " + qualityDesc);
            System.out.println("-------------------------------");
        }

        // Количество спутников
        if (tokens.length > 7 && !tokens[7].isEmpty()) {
            System.out.println("  Спутники: " + tokens[7]);
        }

        // Высота
        if (tokens.length > 9 && !tokens[9].isEmpty()) {
            System.out.println("  Высота: " + tokens[9] + " м");
        }
    }

    /**
     * Парсинг RMC – не все поля.
     */
    private static void parseRMC(String[] tokens) {
        System.out.println("  Тип: RMC (Recommended Minimum)");
        if (tokens.length < 12) return;

        // Статус (A = активно, V = неактивно)
        if (tokens.length > 2 && !tokens[2].isEmpty()) {
            String status = tokens[2];
            System.out.println("  Статус: " + (status.equals("A") ? "Активен" : "Неактивен"));
        }

        // Время и дата
        if (tokens.length > 1 && !tokens[1].isEmpty() && tokens.length > 9 && !tokens[9].isEmpty()) {
            String time = tokens[1];
            String date = tokens[9];
            String formattedTime = time.substring(0, 2) + ":" +
                    time.substring(2, 4) + ":" +
                    time.substring(4, 6);
            String formattedDate = "20" + date.substring(4, 6) + "-" +
                    date.substring(2, 4) + "-" +
                    date.substring(0, 2);
            System.out.println("  Дата/время: " + formattedDate + " " + formattedTime);
        }

        // Широта/Долгота
        if (tokens.length > 3 && !tokens[3].isEmpty() && !tokens[4].isEmpty()) {
            double lat = convertNmeaCoordinate(tokens[3], tokens[4]);
            System.out.printf("  Широта: %.6f%n", lat);
        }
        if (tokens.length > 5 && !tokens[5].isEmpty() && !tokens[6].isEmpty()) {
            double lon = convertNmeaCoordinate(tokens[5], tokens[6]);
            System.out.printf("  Долгота: %.6f%n", lon);
        }

        // Скорость (узлы -> км/ч)
        if (tokens.length > 7 && !tokens[7].isEmpty()) {
            double knots = Double.parseDouble(tokens[7]);
            double kmh = knots * 1.852;
            System.out.printf("  Скорость: %.2f км/ч%n", kmh);
        }

        // Курс
        if (tokens.length > 8 && !tokens[8].isEmpty()) {
            System.out.println("  Курс: " + tokens[8] + "°");
        }
    }

    /**
     * Парсинг GSV – спутники в зоне видимости.
     */
    private static void parseGSV(String[] tokens) {
        System.out.println("  Тип: GSV (Satellites in View)");
        if (tokens.length < 4) return;

        if (!tokens[1].isEmpty() && !tokens[2].isEmpty() && !tokens[3].isEmpty()) {
            System.out.println("  Всего сообщений: " + tokens[1]);
            System.out.println("  Номер сообщения: " + tokens[2]);
            System.out.println("  Всего спутников: " + tokens[3]);
        }

        // Детальная информация по каждому спутнику (4 спутника на сообщение)
        int satCount = (tokens.length - 4) / 4;
        for (int i = 0; i < satCount; i++) {
            int idx = 4 + i * 4;
            if (tokens.length > idx + 3) {
                String prn = tokens[idx];
                String elevation = tokens[idx + 1];
                String azimuth = tokens[idx + 2];
                String snr = tokens[idx + 3];
                System.out.printf("    Спутник %2s: угол=%3s°, азимут=%3s°, SNR=%2s dB%n",
                        prn, elevation, azimuth, snr);
            }
        }
    }

    /**
     * Парсинг GSA – точность и активные спутники.
     */
    private static void parseGSA(String[] tokens) {
        System.out.println("  Тип: GSA (DOP and Active Satellites)");
        if (tokens.length < 17) return;

        // Режим
        if (!tokens[1].isEmpty()) {
            String mode = tokens[1].equals("M") ? "Ручной" : "Авто";
            System.out.println("  Режим: " + mode);
        }
        // Тип фиксации
        if (!tokens[2].isEmpty()) {
            int fix = Integer.parseInt(tokens[2]);
            String fixType = switch (fix) {
                case 1 -> "Нет фиксации";
                case 2 -> "2D";
                case 3 -> "3D";
                default -> "Неизвестно";
            };
            System.out.println("  Фиксация: " + fixType);
        }

        // PDOP, HDOP, VDOP
        if (tokens.length > 15 && !tokens[15].isEmpty()) {
            System.out.println("  PDOP: " + tokens[15]);
        }
        if (tokens.length > 16 && !tokens[16].isEmpty()) {
            System.out.println("  HDOP: " + tokens[16]);
        }
        if (tokens.length > 17 && !tokens[17].isEmpty()) {
            System.out.println("  VDOP: " + tokens[17]);
        }
    }

    /**
     * Конвертирует NMEA координату (ddmm.mmmm) и направление в десятичные градусы.
     */
    private static double convertNmeaCoordinate(String coord, String direction) {
        if (coord.isEmpty()) return 0.0;
        try {
            int dotIndex = coord.indexOf('.');
            if (dotIndex < 2) return 0.0;

            int degrees = Integer.parseInt(coord.substring(0, dotIndex - 2));
            double minutes = Double.parseDouble(coord.substring(dotIndex - 2));
            double decimal = degrees + (minutes / 60.0);

            if (direction.equals("S") || direction.equals("W")) {
                decimal = -decimal;
            }
            return decimal;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
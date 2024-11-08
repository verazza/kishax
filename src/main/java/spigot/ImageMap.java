package spigot;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;

import common.Database;
import common.FMCSettings;
import net.coobird.thumbnailator.Thumbnails;
import net.md_5.bungee.api.ChatColor;

public class ImageMap {
    public static final String PERSISTANT_KEY = "custom_image";
    public static List<String> imagesColumnsList = new ArrayList<>();
    public static List<Integer> thisServerMapIds = new ArrayList<>();
    public static List<String> args2 = new ArrayList<>(Arrays.asList("create", "createqr", "q"));
    private final common.Main plugin;
    private final Logger logger;
    private final Database db;
    private final String serverName;
    @Inject
    public ImageMap(common.Main plugin, Logger logger, Database db, ServerHomeDir shd) {
        this.plugin = plugin;
        this.logger = logger;
        this.db = db;
        this.serverName = shd.getServerName();
    }

    public void executeQ(CommandSender sender, Command command, String label, String[] args, boolean q) {
        if (sender instanceof Player player) {
            int argsLength = args.length;
            String otp;
            // q : true -> /q, false -> /fmc q
            if (q) {
                otp = args[0];
                if (argsLength < 1) {
                    player.sendMessage("使用法: /q <code>");
                    return;
                }
            } else {
                otp = args[1];
                if (argsLength < 2) {
                    player.sendMessage("使用法: /fmc q <code>");
                    return;
                }
            }
            try (Connection conn = db.getConnection()) {
                if (checkOTPIsCorrect(conn, otp)) {
                    player.sendMessage("認証に成功しました。");
                    Map<String, Object> imageInfo = getImageInfoByOTP(conn, otp);
                    String dName = (String) imageInfo.get("name"),
                        dId = (String) imageInfo.get("did"),
                        title = (String) imageInfo.get("title"),
                        comment = (String) imageInfo.get("comment"),
                        url = (String) imageInfo.get("url");
                    Date date = Date.valueOf(((Date) imageInfo.get("date")).toLocalDate());
                    boolean isQr = (boolean) imageInfo.get("isqr");
                    String[] imageArgs = new String[] {"im", isQr ? "createqr" : "create", url, title, comment};
                    // dateを使うので、player.performCommandメソッドを使わず、直接実行させる
                    executeImageMap(sender, command, label, imageArgs, new Object[] {otp, dName, dId, date});
                } else {
                    player.sendMessage(ChatColor.RED + "ワンタイムパスワードが間違っています。");
                }
            } catch (SQLException | ClassNotFoundException e) {
                player.sendMessage("画像の読み込みに失敗しました。");
                logger.error("An SQLException | ClassNotFoundException error occurred: {}", e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
            }
        } else {
            if (sender != null) {
                sender.sendMessage("このコマンドはプレイヤーのみが実行できます。");
            }
        }
    }
    
    public void executeImageMapFromGivingMap(Player player, Object[] mArgs) {
        executeImageMapFromMenu(player, mArgs, true);
    }

    public void executeImageMapFromMenu(Player player, Object[] mArgs) {
        executeImageMapFromMenu(player, mArgs, false);
    }

    private void executeImageMapFromMenu(Player player, Object[] mArgs, boolean isGiveMap) {
        // このサーバーにはないマップを生成する
        try (Connection conn = db.getConnection()) {
            int id = (int) mArgs[0];
            boolean isQr = (boolean) mArgs[1];
            String authorName = (String) mArgs[2],
                imageUUID = (String) mArgs[3],
                title = (String) mArgs[4],
                comment = (String) mArgs[5],
                ext = (String) mArgs[6],
                date = (String) mArgs[7],
                fullPath = FMCSettings.IMAGE_FOLDER.getValue() + "/" + date.replace("-", "") + "/" + imageUUID + "." + ext,
                playerName = player.getName();
            BufferedImage image;
            image = loadImage(fullPath);
            image = !isQr ? resizeImage(image, 128, 128) : image;
            List<String> lores = new ArrayList<>();
            lores.add(isQr ? "<QRコード>" : "<イメージマップ>");
            List<String> commentLines = Arrays.stream(comment.split("\n"))
                                .map(String::trim)
                                .collect(Collectors.toList());
            lores.addAll(commentLines);
            lores.add("created by " + authorName);
            lores.add("at " + date.replace("-", "/"));
            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapView mapView = Bukkit.createMap(player.getWorld());
            int mapId = mapView.getId(); // 一意のmapIdを取得
            mapView.getRenderers().clear();
            mapView.addRenderer(new ImageMapRenderer(logger, image, fullPath));
            var meta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(title);
                meta.setLore(lores);
                meta.setMapView(mapView);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, ImageMap.PERSISTANT_KEY), PersistentDataType.STRING, "true");
                mapItem.setItemMeta(meta);
            }
            Map<String, String> modifyMap = new HashMap<>();
            modifyMap.put("menu", "true");
            modifyMap.put("menuer", "\"" + playerName + "\"");
            modifyMap.put("server", "\"" + serverName + "\"");
            modifyMap.put("mapid", String.valueOf(mapId));
            copyLineFromMenu(conn, id, modifyMap);
            if (isGiveMap) {
                // mapIdを更新する必要がある
                // なぜなら、このサーバーにはないマップを生成するため
                // giveMapメソッドで、そのワールド内にないことは確認済みなので
                db.updateLog(conn, "UPDATE images SET mapid=? WHERE id=?;", new Object[] {mapId, id});
            }
            player.getInventory().addItem(mapItem);
            if (!isGiveMap) {
                player.sendMessage("画像マップを渡しました。");
            }
        } catch (IOException | SQLException | ClassNotFoundException e) {
            player.sendMessage("画像の読み取りに失敗しました。");
            logger.error("An IOException | SQLException | URISyntaxException | ClassNotFoundException error occurred: {}", e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                logger.error(element.toString());
            }
        }
    }

    public void executeImageMap(CommandSender sender, Command command, String label, String[] args, Object[] dArgs) {
        executeImageMap(sender, args, dArgs, false);
    }

    public void executeImageMapForConfirm(CommandSender sender, String[] args) {
        executeImageMap(sender, args, null, true);
    }

    @SuppressWarnings("null")
    private void executeImageMap(CommandSender sender, String[] args, Object[] dArgs, boolean confirm) {
        if (sender instanceof Player player) {
            if (args.length < 3) {
                player.sendMessage("使用法: /fmc im <create|createqr> <url> [Optional: <title> <comment>]");
                return;
            }
            boolean isQr = args[1].equalsIgnoreCase("createqr"),
                fromDiscord = (dArgs != null);
            String playerName = player.getName(),
                playerUUID = player.getUniqueId().toString(),
                imageUUID = UUID.randomUUID().toString(),
                url = args[2],
                title = (args.length > 3 && !args[3].isEmpty()) ? args[3]: "無名のタイトル",
                comment = (args.length > 4 && !args[4].isEmpty()) ? args[4]: "コメントなし",
                ext,
                fullPath;
            try (Connection conn = db.getConnection()) {
                int limitUploadTimes = FMCSettings.IMAGE_LIMIT_TIMES.getIntValue(),
                    playerUploadTimes = getPlayerTodayTimes(conn, playerName),
                    thisTimes = playerUploadTimes + 1;
                if (thisTimes >= limitUploadTimes) {
                    player.sendMessage(ChatColor.RED + "1日の登録回数は"+limitUploadTimes+"回までです。");
                    return;
                }
                if (!isQr && !isValidURL(url)) {
                    player.sendMessage("無効なURLです。");
                    return;
                }
                LocalDate localDate = LocalDate.now();
                String now = fromDiscord ? ((Date) dArgs[3]).toString() : localDate.toString();
                BufferedImage image;
                if (isQr) {
                    ext = "png";
                    fullPath = FMCSettings.IMAGE_FOLDER.getValue() + "/" + now.replace("-", "") + "/" + imageUUID + "." + ext;
                    image = generateQRCodeImage(url);
                    saveImageToFileSystem(image, imageUUID, ext);
                } else {
                    URL getUrl = new URI(url).toURL();
                    HttpURLConnection connection = (HttpURLConnection) getUrl.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();
                    String contentType = connection.getContentType();
                    switch (contentType) {
                        case "image/png" -> ext = "png";
                        case "image/jpeg" -> ext = "jpeg";
                        case "image/jpg" -> ext = "jpg";
                        default -> {
                            player.sendMessage("指定のURLは規定の拡張子を持ちません。");
                            return;
                        }
                    }
                    fullPath = FMCSettings.IMAGE_FOLDER.getValue() + "/" + now.replace("-", "") + "/" + imageUUID + "." + ext;
                    image =  ImageIO.read(getUrl);
                    saveImageToFileSystem(image, imageUUID, ext); // リサイズ前の画像を保存
                    image = resizeImage(image, 128, 128);
                    if (image == null) {
                        player.sendMessage("指定のURLは規定の拡張子を持ちません。");
                        return;
                    }
                }
                List<String> lores = new ArrayList<>();
                lores.add(isQr ? "<QRコード>" : "<イメージマップ>");
                List<String> commentLines = Arrays.stream(comment.split("\n"))
                                  .map(String::trim)
                                  .collect(Collectors.toList());
                lores.addAll(commentLines);
                lores.add("created by " + playerName);
                lores.add("at " + now.replace("-", "/"));
                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                MapView mapView = Bukkit.createMap(player.getWorld());
                int mapId = mapView.getId(); // 一意のmapIdを取得
                mapView.getRenderers().clear();
                mapView.addRenderer(new ImageMapRenderer(logger, image, fullPath));
                var meta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(title);
                    meta.setLore(lores);
                    meta.setMapView(mapView);
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, ImageMap.PERSISTANT_KEY), PersistentDataType.STRING, "true");
                    mapItem.setItemMeta(meta);
                }
                if (fromDiscord) {
                    db.updateLog(conn, "UPDATE images SET name=?, uuid=?, server=?, mapid=?, title=?, imuuid=?, ext=?, url=?, comment=?, isqr=?, otp=?, d=?, dname=?, did=?, date=? WHERE otp=?;", new Object[] {playerName, playerUUID, serverName, mapId, title, imageUUID, ext, url, comment, isQr, null, fromDiscord, (String) dArgs[1], (String) dArgs[2], now, (String) dArgs[0]});
                } else {
                    db.insertLog(conn, "INSERT INTO images (name, uuid, server, mapid, title, imuuid, ext, url, comment, isqr, confirm, date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);", new Object[] {playerName, playerUUID, serverName, mapId, title, imageUUID, ext, url, comment, isQr, confirm, Date.valueOf(LocalDate.now())});
                }
                player.getInventory().addItem(mapItem);
                if (!confirm) {
                    player.sendMessage("画像マップを渡しました。(" + thisTimes + "/" + limitUploadTimes + ")");
                }
            } catch (IOException | SQLException | URISyntaxException | ClassNotFoundException | WriterException e) {
                player.sendMessage("画像のダウンロードまたは保存に失敗しました: " + url);
                logger.error("An IOException | SQLException | URISyntaxException | ClassNotFoundException error occurred: {}", e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
            }
        } else {
            if (sender != null) {
                sender.sendMessage("このコマンドはプレイヤーのみが実行できます。");
            }
        }
    }

    public Map<Integer, Map<String, Object>> getImageMap(Connection conn) throws SQLException, ClassNotFoundException {
        Map<Integer, Map<String, Object>> imageInfo = new HashMap<>();
        String query = "SELECT * FROM images WHERE menu!=? AND confirm!=?;";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setBoolean(1, true);
        ps.setBoolean(2, true);
        ResultSet rs = ps.executeQuery();
        int index = 0;
        while (rs.next()) {
            Map<String, Object> rowMap = new HashMap<>();
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                rowMap.put(columnName, rs.getObject(columnName));
            }
            imageInfo.computeIfAbsent(index, _ -> rowMap);
            index++;
        }
        return imageInfo;
    }

    public Map<Integer, Map<String, Object>> getThisServerImages(Connection conn) throws SQLException, ClassNotFoundException {
        Map<Integer, Map<String, Object>> serverImageInfo = new HashMap<>();
        String query = "SELECT * FROM images WHERE server=?;";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, serverName);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Map<String, Object> rowMap = new HashMap<>();
            int mapId = rs.getInt("mapid");
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                if (!columnName.equals("mapid")) {
                    rowMap.put(columnName, rs.getObject(columnName));
                }
            }
            serverImageInfo.computeIfAbsent(mapId, _ -> rowMap);
        }
        return serverImageInfo;
    }

    public void giveMapToPlayer(Player player, int mapId) {
        AtomicBoolean found = new AtomicBoolean(false);
        for (World world : Bukkit.getWorlds()) {
            for (ItemFrame itemFrame : world.getEntitiesByClass(ItemFrame.class)) {
                ItemStack item = itemFrame.getItem();
                if (item.getType() == Material.FILLED_MAP) {
                    MapMeta mapMeta = (MapMeta) item.getItemMeta();
                    if (mapMeta != null && mapMeta.hasMapView()) {
                        MapView mapView = mapMeta.getMapView();
                        if (mapView != null && mapView.getId() == mapId) {
                            found.set(true);
                            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                            mapMeta.setMapView(mapView);
                            mapItem.setItemMeta(mapMeta);
                            player.getInventory().addItem(mapItem);
                            player.sendMessage("画像マップを渡しました。");
                            return;
                        }
                    }
                }
            }
        }
        if (!found.get()) {
            //player.sendMessage("地図ID " + mapId + " が見つかりません。");
            // このメソッドは、このサーバーにあるマップをプレイヤーに渡すメソッドである。
            // すなわち、このサーバーに過去あった、もしくは現在あることは確定
            // このサーバーというのは確定してる
            // このサーバーにはないマップを生成する
            // ロードして、プレイヤーに上げて、もとのデータベースのmapIDを更新すればいい
            // このサーバーとmapIdで一致するデータを取得
            // そのデータを使って、マップを生成
            // プレイヤーに渡す
            // そのデータベースのmapIdを更新
            try (Connection conn = db.getConnection()) {
                Map<String, Object> mapInfo = getMapInfoForThisServerByMapId(conn, mapId);
                if (!mapInfo.isEmpty()) {
                    int id = (Integer) mapInfo.get("id");
                    boolean isQr = (boolean) mapInfo.get("isqr");
                    String authorName = (String) mapInfo.get("name"),
                        imageUUID = (String) mapInfo.get("imuuid"),
                        title = (String) mapInfo.get("title"),
                        comment = (String) mapInfo.get("comment"),
                        ext = (String) mapInfo.get("ext"),
                        date = ((Date) mapInfo.get("date")).toString();
                    Object[] mArgs = new Object[] {id, isQr, authorName, imageUUID, title, comment, ext, date, mapId};
                    executeImageMapFromGivingMap(player, mArgs);
                }
            } catch (SQLException | ClassNotFoundException e) {
                player.sendMessage("画像の読み取りに失敗しました。");
                logger.error("An SQLException | ClassNotFoundException error occurred: {}", e.getMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                }
            }
        }
    }

    private Map<String, Object> getMapInfoForThisServerByMapId(Connection conn, int mapId) throws SQLException, ClassNotFoundException {
        Map<String, Object> mapInfo = new HashMap<>();
        String query = "SELECT * FROM images WHERE mapid=? AND server=?;";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setInt(1, mapId);
        ps.setString(2, serverName);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                mapInfo.put(columnName, rs.getObject(columnName));
            }
        }
        return mapInfo;
    }

    public String formatDate2(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        return date.format(formatter);
    }

    private void copyLineFromMenu(Connection conn, int id, Map<String, String> modifyMap) throws SQLException, ClassNotFoundException {
        List<String> modifiedImageColumnsList = new ArrayList<>(ImageMap.imagesColumnsList),
            imagesColumnsListCopied = new ArrayList<>(ImageMap.imagesColumnsList);
        for (Map.Entry<String, String> entry : modifyMap.entrySet()) {
            String key = entry.getKey(),
                newValue = entry.getValue();
            modifiedImageColumnsList = modifiedImageColumnsList.stream()
                .map(s -> s.equals(key) ? newValue : s)
                .collect(Collectors.toList());
        }
        // idカラムを除外
        imagesColumnsListCopied.remove("id");
        modifiedImageColumnsList.remove("id");
        String query = "INSERT INTO images (" + String.join(", ", imagesColumnsListCopied) + ") " +
                       "SELECT " + String.join(", ", modifiedImageColumnsList) + " " +
                       "FROM images " +
                       "WHERE id = ?;";
        //logger.info(query);
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setInt(1, id);
        ps.executeUpdate();
    }
    
    private Map<String, Object> getImageInfoByOTP(Connection conn, String otp) throws SQLException, ClassNotFoundException {
        Map<String, Object> imageInfo = new HashMap<>();
        String query = "SELECT * FROM images WHERE otp=?;";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, otp);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                imageInfo.put(columnName, rs.getObject(columnName));
            }
        }
        return imageInfo;
    }

    public int getPlayerTodayTimes(Connection conn, String playerName) throws SQLException, ClassNotFoundException {
        String query = "SELECT COUNT(*) FROM images WHERE menu != ? AND name = ? AND DATE(date) = ?";
		PreparedStatement ps = conn.prepareStatement(query);
        ps.setBoolean(1, true);
		ps.setString(2, playerName);
		ps.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
		ResultSet rs = ps.executeQuery();
		if (rs.next()) {
			return rs.getInt(1);
		}
        return 0;
    }

    public String getFullPath(Date date, String imageUUID, String ext) {
        return FMCSettings.IMAGE_FOLDER.getValue() + "/" + date.toString().replace("-", "") + "/" + imageUUID + "." + ext;
    }

    public BufferedImage loadImage(String filePath) throws IOException {
        File imageFile = new File(filePath);
        if (!imageFile.exists()) {
            logger.error("Image file does not exist: {}", filePath);
            return null;
        }
        return ImageIO.read(imageFile);
    }

    /*public BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = outputImage.createGraphics();
        g2d.drawImage(resultingImage, 0, 0, null);
        g2d.dispose();
        return outputImage;
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        resizedImage.getGraphics().drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        return resizedImage;
    }*/

    public BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
        return Thumbnails.of(originalImage)
            .size(targetWidth, targetHeight)
            .asBufferedImage();
    }

    private boolean checkOTPIsCorrect(Connection conn, String code) throws SQLException, ClassNotFoundException {
        String query = "SELECT * FROM images WHERE otp=? LIMIT 1;";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, code);
        ResultSet rs = ps.executeQuery();
        return rs.next();
    }

    @SuppressWarnings("unused")
    private String formatDate(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return date.format(formatter);
    }

    private boolean isValidURL(String urlString) {
        try {
            URI uri = new URI(urlString);
            uri.toURL();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }
    
    private BufferedImage generateQRCodeImage(String text) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        int width = 128;
        int height = 128;
        var bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    private void saveImageToFileSystem(BufferedImage image, String imageUUID, String ext) throws IOException, SQLException, ClassNotFoundException {
        Path dirPath = Paths.get(FMCSettings.IMAGE_FOLDER.getValue(), LocalDate.now().toString().replace("-", ""));
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        String fileName = imageUUID + "." + ext;
        Path filePath = dirPath.resolve(fileName);
        ImageIO.write(image, ext, filePath.toFile());
    }
}
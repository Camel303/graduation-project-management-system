package newana
import java.io.PrintWriter
import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.types._
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions._
object basic {
  def main(args: Array[String]) {

    // 初始化SparkSession
    val spark = SparkSession.builder()
      .appName("Student Dropout and Success Analysis")
      .master("local[*]") // 本地模式，使用所有可用CPU
      .getOrCreate()

    // 读取HDFS上的数据为RDD
    val filePath = "hdfs://localhost:9000/student/student.csv"
    val rawRDD = spark.sparkContext.textFile(filePath)

    // 提取头部信息，获取列名
    val header = rawRDD.first()
    val dataRDD = rawRDD.filter(row => row != header) // 去掉头部

    // 将分号分隔的字符串转换为Row对象
    val rowRDD = dataRDD.map(line => line.split(";")).map(attributes => Row.fromSeq(attributes))

    // 定义DataFrame的Schema
    val schema = StructType(header.split(";").map(fieldName => StructField(fieldName, StringType, nullable = true)))

    // 将RDD转换为DataFrame
    val dataDF = spark.createDataFrame(rowRDD, schema)

    // 显示数据结构
    dataDF.printSchema()
    dataDF.show(5) // 显示前5行数据以确认正确性
    
    
    
    // 保存数据为JSON文件的函数
    def saveResultsAsJson(df: DataFrame, outputPath: String): Unit = {
      val jsonResult = df.toJSON.collect()  // 将DataFrame转换为JSON格式的字符串集合
      val writer = new PrintWriter(outputPath)
      writer.write("[")
      writer.write(jsonResult.mkString(","))
      writer.write("]")
      writer.close()
      println(s"统计结果已保存到: $outputPath")
    }
    // 统计Target列中的每个值的数量
    val targetStatsDF = dataDF.groupBy("Target")
      .agg(
        count("*").as("count"), // 统计数量
        (count("*") * 100 / dataDF.count()).as("percentage") // 计算百分比
      )

    // 显示Target统计结果
    targetStatsDF.show()
    // 调用保存函数，将分析结果保存为单个JSON文件
    saveResultsAsJson(targetStatsDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j1.json") 
    
    // 筛选出入学成绩和学业结果信息，并将入学成绩转换为数值类型
    val gradeOutcomeDF = dataDF
      .select(
        col("Admission grade").cast(DoubleType).as("AdmissionGrade"),
        col("Target")
      )
      .filter("AdmissionGrade IS NOT NULL") // 过滤空值

    val dropoutRateByGradeDF = gradeOutcomeDF
      .withColumn("AdmissionGrade", round(col("AdmissionGrade")).cast("int")) // 将 AdmissionGrade 四舍五入为整数
      .groupBy("AdmissionGrade")
      .agg(
        count("*").as("total_count"), // 每个成绩的总人数
        sum(when(col("Target") === "Dropout", 1).otherwise(0)).as("dropout_count") // 辍学人数
      )
      .withColumn("dropout_rate", col("dropout_count") * 100 / col("total_count")) // 辍学率百分比
      .orderBy("AdmissionGrade") // 按课程数递增排序

    // 将结果保存为 JSON 格式文件
    saveResultsAsJson(dropoutRateByGradeDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j2.json")
    
    // 筛选出第一学期、第二学期的成绩和学业结果数据，并将成绩转换为数值类型
    val semesterGradesDF = dataDF
  .select(
    col("Curricular units 1st sem (grade)").cast(DoubleType).as("FirstSemGrade"),
    col("Curricular units 2nd sem (grade)").cast(DoubleType).as("SecondSemGrade"),
    col("Target")
  )
  .filter("FirstSemGrade IS NOT NULL AND SecondSemGrade IS NOT NULL") // 过滤掉空值
  .filter("FirstSemGrade != 0.0 AND SecondSemGrade != 0.0")
  .orderBy("Target", "FirstSemGrade", "SecondSemGrade") // 按 Target、FirstSemGrade、SecondSemGrade 递增排序
      
      // 将结果保存为 JSON 格式文件
    saveResultsAsJson(semesterGradesDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j3.json")
    
    
    // 筛选出奖学金和学业结果的数据，并将其转换为数值类型
    val scholarshipDataDF = dataDF
      .select(
        col("Scholarship holder").cast(IntegerType).as("HasScholarship"),
        col("Target") // 学业结果
      )
      .filter("HasScholarship IS NOT NULL AND Target IS NOT NULL") // 过滤空值

    // 计算奖学金情况与学业结果的统计
    val scholarshipOutcomeDF = scholarshipDataDF
      .groupBy("HasScholarship", "Target")
      .agg(
        count("*").as("Count") // 统计每种情况的数量
      )
      .groupBy("HasScholarship")
      .pivot("Target") // 生成以学业结果为列的统计
      .sum("Count")
      .na.fill(0) // 填充缺失值为 0
      

    // 计算辍学率
    val resultWithDropoutRateDF = scholarshipOutcomeDF
      .withColumn("Total", col("Graduate") + col("Dropout")) // 计算总人数
      .withColumn("DropoutRate", col("Dropout") * 100 / col("Total")) // 计算辍学率

    // 将结果保存为 JSON 格式文件
    saveResultsAsJson(resultWithDropoutRateDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j4.json")

    // 筛选出年龄和学业结果的数据，并将其转换为数值类型
    val ageDataDF = dataDF
      .select(
        col("Age at enrollment").cast(IntegerType).as("Age"),
        col("Target") // 学业结果
      )
      .filter("Age IS NOT NULL AND Target IS NOT NULL") // 过滤空值
      .orderBy("Age") // 按课程数递增排序

    // 计算每个年龄的辍学率
    val dropoutRateByAgeDF = ageDataDF
      .groupBy("Age")
      .agg(
        count("*").as("TotalCount"), // 每个年龄的总人数
        sum(when(col("Target") === "Dropout", 1).otherwise(0)).as("DropoutCount") // 辍学人数
      )
      .withColumn("DropoutRate", col("DropoutCount") * 100 / col("TotalCount")) // 计算辍学率

    // 将结果保存为 JSON 格式文件
    saveResultsAsJson(dropoutRateByAgeDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j5.json")

    //筛选出母亲和父亲学历以及辍学信息
    val parentEducationDF = dataDF
      .select(
        col("Mother's qualification").cast(IntegerType).as("MotherQualification"),
        col("Father's qualification").cast(IntegerType).as("FatherQualification"),
        col("Target")
      )

    // 计算母亲学历与辍学率
    val motherDropoutRateDF = parentEducationDF
      .groupBy("MotherQualification")
      .agg(
        count("*").as("total_count"),
        sum(when(col("Target") === "Dropout", 1).otherwise(0)).as("dropout_count")
      )
      .withColumn("dropout_rate", col("dropout_count") * 100 / col("total_count"))
      .orderBy("MotherQualification") // 按课程数递增排序
    motherDropoutRateDF.show()
    // 计算父亲学历与辍学率
    val fatherDropoutRateDF = parentEducationDF
      .groupBy("FatherQualification")
      .agg(
        count("*").as("total_count"),
        sum(when(col("Target") === "Dropout", 1).otherwise(0)).as("dropout_count")
      )
      .withColumn("dropout_rate", col("dropout_count") * 100 / col("total_count"))
      .orderBy("FatherQualification") // 按课程数递增排序
      
    fatherDropoutRateDF.show()
    // 保存母亲学历与辍学率结果为JSON文件
    saveResultsAsJson(motherDropoutRateDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j6.json")

    // 保存父亲学历与辍学率结果为JSON文件
    saveResultsAsJson(fatherDropoutRateDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j7.json")
    
    
     // 筛选出父母职业和学业成果信息
    val parentOccupationDF = dataDF
      .select(
        col("Mother's occupation").cast(IntegerType).as("MotherOccupation"),
        col("Father's occupation").cast(IntegerType).as("FatherOccupation"),
        col("Target")
      )
      .filter("MotherOccupation IS NOT NULL AND FatherOccupation IS NOT NULL") // 过滤空值

    // 计算母亲职业与学业成果的统计
    val motherOccupationOutcomeDF = parentOccupationDF
      .groupBy("MotherOccupation", "Target")
      .agg(
        count("*").as("count") // 统计每个职业和学业成果组合的数量
      )
      .groupBy("MotherOccupation")
      .pivot("Target")
      .sum("count")
      .na.fill(0) // 如果某些组合没有数据，则填充为 0
      .orderBy("MotherOccupation") // 按课程数递增排序

    // 计算父亲职业与学业成果的统计
    val fatherOccupationOutcomeDF = parentOccupationDF
      .groupBy("FatherOccupation", "Target")
      .agg(
        count("*").as("count") // 统计每个职业和学业成果组合的数量
      )
      .groupBy("FatherOccupation")
      .pivot("Target")
      .sum("count")
      .na.fill(0) // 如果某些组合没有数据，则填充为 0
      .orderBy("FatherOccupation") // 按课程数递增排序

    // 保存母亲职业与学业成果结果为JSON文件
    saveResultsAsJson(motherOccupationOutcomeDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j8.json")

    // 保存父亲职业与学业成果结果为JSON文件
    saveResultsAsJson(fatherOccupationOutcomeDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j9.json")

     // 筛选出第一学期和第二学期的课程数与成绩的数据，并将其转换为数值类型
    val semesterDataDF = dataDF
      .select(
        col("Curricular units 1st sem (enrolled)").cast(IntegerType).as("FirstSemUnits"),
        col("Curricular units 1st sem (grade)").cast(DoubleType).as("FirstSemGrade"),
        col("Curricular units 2nd sem (enrolled)").cast(IntegerType).as("SecondSemUnits"),
        col("Curricular units 2nd sem (grade)").cast(DoubleType).as("SecondSemGrade")
      )
      .filter("FirstSemUnits IS NOT NULL AND FirstSemGrade IS NOT NULL AND SecondSemUnits IS NOT NULL AND SecondSemGrade IS NOT NULL") // 过滤空值

    // 创建两个DataFrame，一个用于第一学期，另一个用于第二学期
    val firstSemesterDF = semesterDataDF.select(
      col("FirstSemUnits").as("Units"),
      col("FirstSemGrade").as("Grade")
    ).withColumn("Semester", lit("First Semester")) // 标记为第一学期

    val secondSemesterDF = semesterDataDF.select(
      col("SecondSemUnits").as("Units"),
      col("SecondSemGrade").as("Grade")
    ).withColumn("Semester", lit("Second Semester")) // 标记为第二学期

    // 合并两个DataFrame
    val combinedDF = firstSemesterDF.union(secondSemesterDF)

    // 计算每个课程数对应的平均成绩
    val averageGradesByUnitsDF = combinedDF
      .groupBy("Units")
      .agg(
        avg("Grade").as("AverageGrade") // 计算平均成绩
      )
      .na.fill(0) // 填充缺失值为 0
      .orderBy("Units") // 按课程数递增排序

    // 将结果保存为 JSON 格式文件
    saveResultsAsJson(averageGradesByUnitsDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j10.json")

    // 筛选出所需数据并转换为数值类型
val economicDataDF = dataDF
  .select(
    col("Target"), // 学业结果
    col("Unemployment rate").cast(DoubleType).as("UnemploymentRate"),
    col("Inflation rate").cast(DoubleType).as("InflationRate"),
    col("GDP").cast(DoubleType).as("GDP")
  )
  .filter("UnemploymentRate IS NOT NULL AND InflationRate IS NOT NULL AND GDP IS NOT NULL AND Target IS NOT NULL") // 过滤空值

// 生成失业率与学业结果的关系
val unemploymentJsonDF = economicDataDF
  .select("Target", "UnemploymentRate")
  .orderBy("Target", "UnemploymentRate") // 按失业率递增排序

saveResultsAsJson(unemploymentJsonDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j11.json")

// 生成通货膨胀率与学业结果的关系
val inflationJsonDF = economicDataDF
  .select("Target", "InflationRate")
  .orderBy("Target", "InflationRate") // 按通货膨胀率递增排序

saveResultsAsJson(inflationJsonDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j12.json")

// 生成GDP与学业结果的关系
val gdpJsonDF = economicDataDF
  .select("Target", "GDP")
  .orderBy("Target", "GDP") // 按GDP递增排序

saveResultsAsJson(gdpJsonDF, "C:/Users/73269/Desktop/ab/2024/大三下/Web系统设计/code/web20240307/student/new/j13.json")
    
    
    spark.stop()
  }
}
